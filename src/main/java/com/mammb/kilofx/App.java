package com.mammb.kilofx;

import javafx.animation.*;
import javafx.application.*;
import javafx.scene.*;
import javafx.scene.effect.*;
import javafx.stage.*;
import javafx.util.*;
import javafx.beans.property.*;
import javafx.geometry.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

/**
 * Minimal editor app.
 */
public class App extends Application {

    /**
     * Settings for app.
     */
    static abstract class Settings {
        static final double windowWidth = 900;
        static final double windowHeight = 800;
        static final Color background = Color.web("#2e3032");
        static final Font font = Font.font("Consolas", FontWeight.NORMAL, FontPosture.REGULAR, 16);
        static final int tabSize = 4;
        static final String[] keywords = new String[] {
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "extends", "false", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "null", "package",
            "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch",
            "synchronized", "this", "throw", "throws", "transient", "true", "try", "void", "volatile", "while" };
    }

    /**
     * App entry point.
     * @param args command line parameters
     */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        TextArea textArea = new TextArea(getParameters(), new StringBuffer(), stage);
        Scene scene = new Scene(new StackPane(textArea), Settings.windowWidth, Settings.windowHeight);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * TextArea node.
     */
    public class TextArea extends Region {

        private final Stage stage;
        private final SessionBuffer session;
        private final Text text;
        private final Side side;
        private final Caret caret;
        private final ImePalette imePalette;
        private final SelectionLayer selection;
        private final HighlightLayer highlightLayer;

        private final IntegerProperty viewOriginPos = new SimpleIntegerProperty(0);
        private final IntegerProperty viewOriginLine = new SimpleIntegerProperty(0);
        private final IntegerProperty caretLine = new SimpleIntegerProperty(0);
        private final BooleanProperty imeOn = new SimpleBooleanProperty(false);
        private final double lineHeight;
        private int prefCol = 0;

        public TextArea(Parameters params, StringBuffer sb, Stage stage) {
            this.stage = stage;
            this.stage.setTitle("untitled");
            this.session = new SessionBuffer(sb, Settings.tabSize);
            this.text = createText("", Color.WHITESMOKE);
            this.text.setCursor(Cursor.TEXT);
            this.side = new Side(viewOriginLine);
            this.caret = new Caret(text);
            this.caret.setLayoutY(text.getBaselineOffset());
            this.imePalette = new ImePalette(this);
            this.selection = new SelectionLayer(this);
            this.selection.setLayoutY(text.getBaselineOffset());
            this.highlightLayer = new HighlightLayer(text);
            this.lineHeight = getTextHeight();
            initComponent();
            initHandler();
            params.getUnnamed().stream().findFirst().ifPresent(s -> open(new File(s)));
        }

        private void clear() {
            viewOriginPos.set(0);
            viewOriginLine.set(0);
            caretLine.set(0);
            prefCol = 0;
            imeOn.set(false);
        }

        private void initComponent() {
            setBackground(new Background(new BackgroundFill(Settings.background, null, null)));
            setFocusTraversable(true);
            setInputMethodRequests(createInputMethodRequests());
            setAccessibleRole(AccessibleRole.TEXT_AREA);

            BorderPane pane = new BorderPane();
            Pane main = new Pane(text, highlightLayer, selection, caret, imePalette);
            Pane left = new StackPane(side);
            pane.setLeft(left);
            BorderPane.setMargin(left, new Insets(2, 4, 0, 0));
            pane.setCenter(main);
            BorderPane.setMargin(main, new Insets(2, 0, 0, 0));
            getChildren().add(pane);

            showText();
        }

        private void initHandler() {
            setOnInputMethodTextChanged(this::handleInputMethod);
            setOnKeyPressed(this::handleKeyPressed);
            setOnKeyTyped(this::handleInput);
            setOnScroll(this::handleScroll);
            setOnMouseClicked(this::handleMouseClicked);
            setOnMouseDragged(this::handleMouseDragged);
            text.caretPositionProperty().addListener((b, o, n) -> writeTitle());
            stage.heightProperty().addListener((b, o, n) -> showText());
        }

        private void handleInputMethod(InputMethodEvent e) {
            imeOn.set(true);
            if (e.getCommitted().length() > 0) {
                imeOn.set(false);
                session.add(e.getCommitted());
                showText();
            } else if (!e.getComposed().isEmpty()) {
                imePalette.setText(e.getComposed().stream()
                        .map(InputMethodTextRun::getText).collect(Collectors.joining()));
            }
            if (e.getCommitted().length() == 0 && e.getComposed().isEmpty()) {
                imeOn.set(false);
            }
        }

        private void handleKeyPressed(KeyEvent e) {

            if (imeOn.get()) {
                if (e.getCode() == KeyCode.ESCAPE) imeOn.set(false);
                return;
            }

            if (SC_O.match(e)) {
                open();
                return;
            } else if (SC_S.match(e)) {
                if (!session.save()) saveAs();
                return;
            } else if (SC_SA.match(e)) {
                saveAs();
                return;
            } else if (SC_C.match(e)) {
                selection.copyToClipboard();
                return;
            } else if (SC_V.match(e)) {
                pasteFromClipboard();
                return;
            } else if (SC_X.match(e)) {
                selection.cutToClipboard();
                showText();
                return;
            } else if (SC_Z.match(e)) {
                undo();
                showText();
                return;
            } else if (SC_SZ.match(e)) {
                redo();
                showText();
                return;
            }

            scrollToCaretOr();
            switch (e.getCode()) {
                case UP:         selectionFilter(e, ke -> arrowUp()); return;
                case DOWN:       selectionFilter(e, ke -> arrowDown()); return;
                case PAGE_UP:    selectionFilter(e, ke -> pageUp()); return;
                case PAGE_DOWN:  selectionFilter(e, ke -> pageDown()); return;
                case RIGHT:      selectionFilter(e, ke -> arrowRight()); return;
                case LEFT:       selectionFilter(e, ke -> arrowLeft()); return;
                case HOME:       selectionFilter(e, ke -> home()); return;
                case END:        selectionFilter(e, ke -> end()); return;
                case DELETE:     delete(); return;
                case BACK_SPACE: backSpace(); return;
            }
        }

        private void selectionFilter(KeyEvent e, Consumer<KeyEvent> consumer) {
                 if (e.isShiftDown() && !selection.on()) selection.start();
            else if (!e.isShiftDown() && selection.on()) selection.clear();
            consumer.accept(e);
        }

        private void handleInput(KeyEvent e) {
            if (isChar.test(e) && e.getCharacter().length() > 0) {
                selection.clear();
                if (e.getCharacter().contains("\n") || e.getCharacter().contains("\r")) {
                    String str = session.getLines(session.getPosition(), 1);
                    String leading = str.substring(0, str.length() - str.stripLeading().length());
                    // auto indent - add leading whitespace(single whitespace is ignored)
                    session.add(System.lineSeparator() + (" ".equals(leading) ? "" : leading));
                    caretLine.set(caretLine.get() + 1);
                } else {
                    session.add(e.getCharacter());
                }
                showText();
            }
        }

        private void handleScroll(ScrollEvent e) {
            if (e.getEventType() == ScrollEvent.SCROLL) {
                     if (e.getDeltaY() > 2)  scrollDown(2);
                else if (e.getDeltaY() > 0)  scrollDown(1);
                else if (e.getDeltaY() < -2) scrollUp(2);
                else if (e.getDeltaY() < 0)  scrollUp(1);
            }
        }

        private void handleMouseClicked(MouseEvent e) {

            if (imeOn.get())  return;
            if (!e.getButton().equals(MouseButton.PRIMARY)) return;

            if (selection.on()) {
                if (selection.isDragging()) selection.releaseDragging();
                else selection.clear();
            }

            HitInfo hit = text.hitTest(text.sceneToLocal(new Point2D(e.getSceneX(), e.getSceneY())));
            if (e.getClickCount() == 1) {
                moveCaret(viewOriginPos.get() + hit.getInsertionIndex(), true);
            } else if (e.getClickCount() == 2) {
                int start = session.consecutiveLeft(viewOriginPos.get() + hit.getCharIndex());
                int end  = session.consecutiveRight(viewOriginPos.get() + hit.getCharIndex());
                moveCaret(start, false);
                selection.start();
                moveCaret(end, true);
            }
        }

        private void handleMouseDragged(MouseEvent e) {
            if (imeOn.get())  return;
            if (!e.getButton().equals(MouseButton.PRIMARY)) return;

            HitInfo hit = text.hitTest(text.sceneToLocal(new Point2D(e.getSceneX(), e.getSceneY())));
            moveCaret(viewOriginPos.get() + hit.getInsertionIndex(), true);
            if (!selection.isDragging()) selection.startDrag();
        }

        private void arrowRight() {
            int pos = session.getPosition();
            session.forward(1);
            prefCol = session.getVisualColSize();
            if (session.countLines(pos, session.getPosition()) > 0) {
                caretLine.set(caretLine.get() + 1);
                if (caretLine.get() - viewOriginLine.get() + 1 >= viewportLineSize()) {
                    scrollUp(1);
                    return;
                }
            }
            syncCaret();
        }

        private void arrowLeft() {
            int pos = session.getPosition();
            session.back(1);
            prefCol = session.getVisualColSize();
            if (session.countLines(session.getPosition(), pos) > 0) {
                caretLine.set(caretLine.get() - 1);
                if (caretLine.get() < viewOriginLine.get()) {
                    scrollDown(1);
                    return;
                }
            }
            syncCaret();
        }

        private void arrowUp() {
            session.up(1);
            session.visualColInLine(prefCol);
            caretLine.set(Math.max(caretLine.get() - 1, 0));
            if (caretLine.get() - 1 < viewOriginLine.get()) {
                scrollDown(1);
            } else {
                syncCaret();
            }
        }

        private void arrowDown() {
            int pos = session.getPosition();
            session.down(1);
            session.visualColInLine(prefCol);
            caretLine.set(caretLine.get() + session.countLines(pos, session.getPosition()));
            if (caretLine.get() - viewOriginLine.get() > viewportLineSize() - 2) {
                scrollUp(1);
            } else {
                syncCaret();
            }
        }

        private void pageUp() {
            final int line = viewportLineSize() - 1;
            session.up(line);
            caretLine.set(Math.max(caretLine.get() - line, 0));
            scrollDown(line);
        }

        private void pageDown() {
            final int line = viewportLineSize() - 1;
            int pos = session.getPosition();
            session.down(line);
            int n = session.countLines(pos, session.getPosition());
            caretLine.set(caretLine.get() + n);
            scrollUp(line);
        }

        private void moveCaret(int toPos, boolean followPrefCol) {
            int direction = (toPos >= session.getPosition()) ? 1 : -1;
            int n = session.countLines(session.getPosition(), toPos) * direction;
            session.setPosition(toPos);
            caretLine.set(caretLine.get() + n);
            prefCol = followPrefCol ? session.getVisualColSize() : prefCol;
            syncCaret();
        }

        private void home() {
            session.moveToHeadOfLine();
            prefCol = session.getVisualColSize();
            syncCaret();
        }

        private void end() {
            session.moveToTailOfLine();
            prefCol = session.getVisualColSize();
            syncCaret();
        }

        private void scrollToCaretOr() {
            if (caretLine.get() < viewOriginLine.get()) {
                scrollDown(viewOriginLine.get() - caretLine.get());
            } else if (caretLine.get() > viewOriginLine.get() + viewportLineSize() - 2) {
                scrollUp(caretLine.get() - (viewOriginLine.get() + viewportLineSize() - 2));
            }
        }

        private void scrollUp(int n) {
            for (int i = 0; i < n; i++) {
                int current = viewOriginPos.get();
                if (session.isLastLine(session.getNextLinePos(current, viewportLineSize() - 2))) break;
                int nextPos = session.getNextLinePos(current);
                if (nextPos <= current) break;
                viewOriginPos.set(nextPos);
                viewOriginLine.set(viewOriginLine.get() + 1);
            }
            showText();
        }

        private void scrollDown(int n) {
            for (int i = 0; i < n; i++) {
                if (viewOriginPos.get() == 0) break;
                int nextPos = session.getPrevLinePos(viewOriginPos.get());
                viewOriginPos.set(nextPos);
                viewOriginLine.set(viewOriginLine.get() - 1);
            }
            showText();
        }

        private void showText() {
            Platform.runLater(() -> {
                text.setText(session.getLines(viewOriginPos.get(), viewportLineSize()));
                syncCaret();
                highlightLayer.show();
            });
        }

        private void delete() {
            if (selection.on()) selection.delete();
            else session.delete();
            showText();
        }

        private void backSpace() {
            if (selection.on()) {
                selection.delete();
            } else {
                if (session.isHeadOfLine() && caretLine.get() > 0) {
                    caretLine.set(caretLine.get() - 1);
                }
                session.backSpace();
            }
            showText();
        }

        private void pasteFromClipboard() {
            String str = Clipboard.getSystemClipboard().getString();
            int lineCount = (int) str.chars().filter(c -> c == '\n').count();
            session.add(str);
            caretLine.set(caretLine.get() + lineCount);
            showText();
        }

        private void undo() {
            if (session.getUndoPos() == -1) return;
            if (session.getUndoPos() == session.getPosition()) {
                session.undo();
            } else {
                moveCaret(session.getUndoPos(), false);
                scrollToCaretOr();
            }
        }

        private void redo() {
            if (session.getRedoPos() == -1) return;
            if (session.getRedoPos() == session.getPosition()) {
                session.redo();
            } else {
                moveCaret(session.getRedoPos(), false);
                scrollToCaretOr();
            }
        }

        private void writeTitle() {
            String fileName = session.getFileName().length() == 0 ? "untitled" : session.getFileName();
            stage.setTitle(fileName + " - [Line:" + (caretLine.get() + 1) +
                    ", Pos:" + session.getPosition() + "/" + session.maxPos() + "]");
        }

        private int viewportLineSize() {
            return (int) Math.ceil(getHeight() / lineHeight);
        }

        private void syncCaret() {
            text.setCaretPosition(session.getPosition() - viewOriginPos.get());
        }

        private void open() {
            open(fileChooseOpen(stage));
        }

        private void open(File file) {
            if (file == null || !file.exists() || !file.isFile() || !file.canRead()) return;
            session.open(file);
            clear();
            showText();
        }

        private void saveAs() {
            File file = fileChooseSave(stage);
            if (file == null) return;
            session.saveAs(file);
            stage.setTitle(session.getFileName());
        }

        private InputMethodRequests createInputMethodRequests() {
            return new InputMethodRequests() {
                @Override public Point2D getTextLocation(int offset) {
                    Bounds boundsInScreen = localToScreen(text.getBoundsInParent());
                    return new Point2D(
                            boundsInScreen.getMinX() + caret.getLayoutX() + caret.getMaxX()
                                    + side.getLayoutBounds().getWidth(),
                            boundsInScreen.getMinY() + caret.getLayoutY() + caret.getMaxY());
                }
                @Override public int getLocationOffset(int x, int y) {
                    return 0;
                }
                @Override public void cancelLatestCommittedText() {
                    imeOn.set(false);
                }
                @Override public String getSelectedText() {
                    return "";
                }
            };
        }
    }

    /**
     * Side region.
     */
    static class Side extends Region {

        private final Text text;
        private final double lineHeight;

        public Side(IntegerProperty viewOriginLine) {
            setBackground(new Background(new BackgroundFill(Settings.background, null, null)));
            setStyle("-fx-border-width: 0 1 0 0; -fx-border-color: #6d6d6d;");

            Text sample = createText("0000000", Color.GRAY);
            setPrefWidth(sample.getLayoutBounds().getWidth());
            lineHeight = getTextHeight(sample);

            text = createText("", Color.GRAY);
            text.setLayoutY(text.getBaselineOffset());
            getChildren().add(text);
            viewOriginLine.addListener((b, o, n) -> draw(n.intValue()));
            draw(0);
        }

        private void draw(int lineNo) {
            int lines = (int) (Screen.getPrimary().getVisualBounds().getHeight() / lineHeight);
            text.setText(IntStream.range(lineNo, lineNo + lines)
                .mapToObj(i -> String.format("%6d\n", i + 1)).collect(Collectors.joining()));
        }
    }

    /**
     * Caret.
     */
    static class Caret extends Path {

        private final Timeline timeline = new Timeline();

        public Caret(Text text) {
            setStrokeWidth(2);
            setStroke(Color.WHITESMOKE);
            setManaged(false);
            text.caretShapeProperty().addListener((o, oldVal, newVal) -> handleShape(newVal));
            timeline.setCycleCount(-1);
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(500), e -> setVisible(!isVisible())));
            timeline.play();
        }

        private void handleShape(PathElement... elements) {
            timeline.stop();
            setVisible(true);
            getElements().setAll(elements);
            timeline.play();
        }

        public double getMaxX() {
            return getLayoutBounds().getMaxX();
        }

        public double getMaxY() {
            return getLayoutBounds().getMaxY();
        }
    }

    /**
     * Selection layer.
     */
    public static class SelectionLayer extends Path {

        private final TextArea textArea;
        private int startPos = 0;
        private int endPos = 0;
        private boolean on = false;
        private boolean dragging = false;

        public SelectionLayer(TextArea textArea) {
            setVisible(false);
            setFill(Color.AQUA);
            setStrokeWidth(0);
            setOpacity(0.3);
            setBlendMode(BlendMode.LIGHTEN);
            this.textArea = textArea;
            textArea.text.caretPositionProperty().addListener((o, oldVal, newVal) ->
                    handleCaret(oldVal.intValue(), newVal.intValue()));
        }

        public void start() {
            startPos = endPos = textArea.session.getPosition();
            on = true;
            dragging = false;
            setVisible(true);
        }

        public void startDrag() {
            start();
            dragging = true;
        }

        public void releaseDragging() {
            dragging = false;
        }

        public boolean isDragging() {
            return dragging;
        }

        public void handleCaret(int oldValue, int newValue) {
            if (on) {
                endPos = textArea.session.getPosition();
                int start = Math.min(startPos, endPos);
                int end   = Math.max(startPos, endPos);
                getElements().setAll(textArea.text.rangeShape(
                        Math.max(start - textArea.viewOriginPos.get(), 0),
                        Math.min(end - textArea.viewOriginPos.get(), textArea.text.getText().length())));
            }
        }

        public void clear() {
            setVisible(false);
            on = dragging = false;
            startPos = endPos = 0;
            getElements().clear();
        }

        public boolean on() {
            return on;
        }

        public void copyToClipboard() {
            if (!on) return;
            Map<DataFormat, Object> content = new HashMap<>();
            String text = textArea.session.text(startPos, endPos);
            content.put(DataFormat.PLAIN_TEXT, text);
            Clipboard.getSystemClipboard().setContent(content);
        }

        public void cutToClipboard() {
            if (!on) return;
            copyToClipboard();
            delete();
        }

        public void delete() {
            if (!on) return;
            int captured1 = startPos;
            int captured2 = endPos;
            textArea.moveCaret(Math.min(startPos, endPos), false);
            textArea.session.remove(captured1, captured2);
            clear();
        }
    }

    /**
     * Highlight layer.
     */
    static class HighlightLayer extends Region {

        private Text pear;

        public HighlightLayer(Text pear) {
            this.pear = pear;
        }

        public void show() {
            getChildren().clear();
            // TODO optimize render
            parseKeyword(pear.getText()).forEach(pt -> getChildren().add(pt));
            parseLineComment(pear.getText()).forEach(pt -> getChildren().add(pt));
            parseBlockComment(pear.getText()).forEach(pt -> getChildren().add(pt));
        }

        private List<PosText> parseKeyword(String str) {
            List<PosText> list = new ArrayList<>();
            for (String keyword : Settings.keywords) {
                for (int pos : parseKeyword(str, keyword)) {
                    list.add(createPosText(pos, keyword, Color.ORANGE));
                }
            }
            return list;
        }

        private List<Integer> parseKeyword(String str, String keyword) {
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < str.length(); ) {
                int n = str.indexOf(keyword, i);
                if (n == -1) break;
                int prev = (n == 0) ? -1 : str.codePointAt(n - 1);
                int next = (n + keyword.length() + 1 >= str.length()) ? -1
                        : str.codePointAt(n + keyword.length());
                if ((prev == -1 || (!Character.isAlphabetic(prev) && !Character.isDigit(prev))) &&
                    (next == -1 || (!Character.isAlphabetic(next) && !Character.isDigit(next)))) {
                    list.add(n);
                }
                i = n + 1;
            }
            return list;
        }

        private List<PosText> parseLineComment(String str) {
            List<PosText> list = new ArrayList<>();
            for (int p = 0; p < str.length();) {
                int s = str.indexOf("//", p);
                if (s == -1) break;
                int e = str.indexOf("\n", s + 2);
                if (e == -1) e = str.length();
                list.add(createPosText(s, str.substring(s, e), Color.GRAY));
                p = e + 1;
            }
            return list;
        }

        private List<PosText> parseBlockComment(String str) {
            List<PosText> list = new ArrayList<>();
            for (int p = 0; p < str.length();) {
                int s = str.indexOf("/*", p);
                if (s == -1) break;
                int e = str.indexOf("*/", s + 2);
                if (e == -1) e = str.length() - 2;
                for (String line : split(str.substring(s, e + 2), '\n')) {
                    list.add(createPosText(s, removeLf(line), Color.LIGHTGREEN));
                    s += line.length();
                }
                p = e + 1;
            }

            int s = str.indexOf("*/");
            if (s != -1 && !str.substring(0, s).contains("/*")) {
                int p = 0;
                for (String line : split(str.substring(0, s + 2), '\n')) {
                    list.add(createPosText(p, removeLf(line), Color.LIGHTGREEN));
                    p += line.length();
                }
            }

            return list;
        }

        private PosText createPosText(int viewPos, String str, Color color) {
            PathElement[] shape = pear.caretShape(viewPos, true);
            PosText posText = new PosText(viewPos, createText(str, color));
            posText.setLayoutX(getPathMinX(shape));
            posText.setLayoutY(getPathMinY(shape) + pear.getBaselineOffset());
            return posText;
        }

        static class PosText extends Region {
            final int pos;
            final Text text;
            public PosText(int pos, Text text) {
                this.pos = pos;
                this.text = text;
                getChildren().add(text);
                setBackground(new Background(new BackgroundFill(Settings.background, null, null)));
            }
        }
    }

    /**
     * Input method editing palette.
     */
    static class ImePalette extends Region {

        private TextArea textArea;
        private Text palette;
        private Text original;

        public ImePalette(TextArea textArea) {
            this.textArea = textArea;
            this.palette  = createText("", Color.WHITESMOKE);
            this.original = createText("", Color.WHITESMOKE);
            palette.setUnderline(true);
            setVisible(false);
            setBackground(new Background(new BackgroundFill(Settings.background, null, null)));
            getChildren().setAll(palette, original);
            textArea.imeOn.addListener((observable, oldValue, newValue) -> handle(newValue));
        }

        private void handle(boolean imeOn) {
            setVisible(imeOn);
            if (imeOn) {
                original.setText(textArea.session.getLineRight());
                PathElement[] shape = textArea.text.caretShape(textArea.text.getCaretPosition(), true);
                setLayoutX(getPathMinX(shape));
                setLayoutY(getPathMinY(shape) + palette.getBaselineOffset());
            } else {
                original.setText("");
                palette.setText("");
            }
        }

        public void setText(String text) {
            palette.setText(text);
            original.setLayoutX(palette.getLayoutBounds().getWidth());
        }
    }

    /**
     * Backend buffer of text to be manipulated.
     */
    public static class SessionBuffer {

        /** Text buffer. */
        private final StringBuffer sb;
        /** Tab size. */
        private final int tabSize;
        /** Position of caret. [0..sb.length()] */
        private int position = 0;
        /** Target file. */
        private File file;

        private final Deque<History> undo = new ArrayDeque<>();
        private final Deque<History> redo = new ArrayDeque<>();

        public SessionBuffer(StringBuffer sb, int tabSize) {
            this.sb = sb;
            this.tabSize = tabSize;
        }

        public void clear() {
            file = null;
            sb.setLength(0);
            position = 0;
            undo.clear();
            redo.clear();
        }

        public void add(String text) {
            pushToUndo(History.insertOf(position, text), false);
            add(position, text);
        }

        private void add(int pos, String text) {
            sb.insert(pos, text);
            position = pos + text.length();
        }

        public void remove(int fromPos, int toPos) {
            int min = Math.min(fromPos, toPos);
            int max = Math.max(fromPos, toPos);
            pushToUndo(History.deleteOf(min, text(min, max)), false);
            delete(min, max - min);
        }

        private void delete(int pos, int length) {
            setPosition(pos);
            sb.delete(pos, pos + length);
        }

        public void undo() {
            if (!undo.isEmpty()) redo.push(playback(undo.pop()));
        }

        public void redo() {
            if (!redo.isEmpty()) pushToUndo(playback(redo.pop()), true);
        }

        private void pushToUndo(History history, boolean readyForRedo) {
            undo.push(history);
            if (undo.size() > 1000) undo.removeLast();
            if (!readyForRedo) redo.clear();
        }

        public int getUndoPos() {
            return undo.isEmpty() ? -1 : undo.peek().getToPos();
        }
        public int getRedoPos() {
            return redo.isEmpty() ? -1 : redo.peek().pos;
        }

        private History playback(History history) {
            if (history.del) add(history.pos, history.str);
            else delete(history.pos, history.str.length());
            return history.inverse();
        }

        public void delete() {
            if (position >= sb.length()) return;
            int n = (sb.charAt(position) == '\r' &&
                    position + 1 < sb.length() && sb.charAt(position + 1) == '\n') ? 2 : 1;
            remove(position, position + n);
        }

        public void backSpace() {
            if (position <= 0) return;
            int n = (sb.charAt(position - 1) == '\n' &&
                     position - 2 >= 0 && sb.charAt(position - 2) == '\r') ? 2 : 1;
            remove(position - n, position);
        }

        public void forward(int n) {
            setPosition(position + n);
        }

        public void back(int n) {
            setPosition(position - n);
        }

        public void moveToHeadOfLine() {
            position = getHeadOfLinePos(position);
        }

        public void moveToTailOfLine() {
            position = getTailOfLinePos(position);
        }

        public void up(int line) {
            if (line <= 0) return;
            int next = getHeadOfLinePos(position);
            if (next == 0) return;

            for (int i = 0; i < line; i++) {
                next = getHeadOfLinePos(next - 1);
            }
            setPosition(next);
        }

        public void down(int line) {
            if (line <= 0) return;
            if (isLastLine(position)) return;

            int next = position;
            for (int i = 0; i < line; i++) {
                next = getTailOfLinePos(next) + 1;
            }
            setPosition(next);
        }

        public int getVisualColSize() {
            int tabs = countCharacter(getHeadOfLinePos(position), position, '\t');
            return position - getHeadOfLinePos(position) - tabs + tabs * tabSize;
        }

        public void visualColInLine(int visualColSize) {
            if (visualColSize <= 0) return;
            int count = visualColSize;
            for (int i = getHeadOfLinePos(position); i < getTailOfLinePos(position); i++) {
                count -= (sb.codePointAt(i) == '\t') ? tabSize : 1;
                if (count < 0) {
                    position = Math.min(getTailOfLinePos(position), i);
                    return;
                }
            }
            position = getTailOfLinePos(position);
        }

        public void setPosition(int pos) {
            position = fitInRange(pos);
        }

        public String getLines(int pos, int nLine) {
            if (nLine <= 0) return "";
            int next = pos;
            for (int i = 0; i < nLine; i++) {
                next = getTailOfLinePos(next) + 1;
            }
            return text(getHeadOfLinePos(pos), next);
        }

        public String getLineRight() {
            return text(position, getTailOfLinePos(position));
        }

        public int consecutiveLeft(int pos) {
            int p = fitInRange(pos);
            int type = Character.getType(Character.toLowerCase(sb.codePointAt(p)));
            for (int i = fitInRange(p - 1); i >= 0; i--) {
                if (type != Character.getType(Character.toLowerCase(sb.codePointAt(i)))) {
                    return i + 1;
                }
            }
            return 0;
        }

        public int consecutiveRight(int pos) {
            int p = fitInRange(pos);
            int type = Character.getType(Character.toLowerCase(sb.codePointAt(p)));
            for (int i = fitInRange(p + 1); i < sb.length(); i++) {
                if (type != Character.getType(Character.toLowerCase(sb.codePointAt(i)))) {
                    return i;
                }
            }
            return sb.length();
        }

        public int countLines(int fromPos, int toPos) {
            return countCharacter(fromPos, toPos, '\n');
        }

        private int countCharacter(int fromPos, int toPos, int ch) {
            return (int) text(fromPos, toPos).chars().filter(c -> c == ch).count();
        }

        public String text(int fromPos, int toPos) {
            return sb.substring(fitInRange(Math.min(fromPos, toPos)), fitInRange(Math.max(fromPos, toPos)));
        }

        public int getNextLinePos(int pos) {
            return fitInRange(getTailOfLinePos(pos) + 1);
        }

        public int getNextLinePos(int pos, int n) {
            int ret = pos;
            for (int i = 0; i < n; i++) {
                ret = getNextLinePos(ret);
                if (isLastLine(ret)) return ret;
            }
            return ret;
        }

        public int getPrevLinePos(int pos) {
            return getHeadOfLinePos(fitInRange(getHeadOfLinePos(pos) - 1));
        }

        public boolean isHeadOfLine() {
            return (position == 0) || sb.charAt(position - 1) == '\n';
        }

        public boolean save() {
            if (file == null) return false;
            saveAs(file);
            return true;
        }

        public void saveAs(File file) {
            try {
                Files.writeString(file.toPath(), sb.toString());
                this.file = file;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public void open(File file) {
            try {
                String text = Files.readString(file.toPath());
                clear();
                this.file = file;
                sb.append(text);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public int getPosition() {
            return position;
        }

        public String getFileName() {
            return Objects.isNull(file) ? "" : file.getName();
        }

        public int maxPos() {
            return sb.length();
        }

        private int fitInRange(int pos) {
            return (pos < 0) ? 0 : Math.min(pos, maxPos());
        }

        int getHeadOfLinePos(int pos) {
            for (int i = pos - 1; i >= 0; i--) {
                if (sb.charAt(i) == '\n') {
                    return i + 1;
                }
            }
            return 0;
        }

        int getTailOfLinePos(int pos) {
            if (pos > (sb.length() - 1)) return maxPos();
            int index = sb.indexOf("\n", pos);
            return (index == -1) ? maxPos() : index;
        }

        boolean isLastLine(int pos) {
            return sb.indexOf("\n", pos) == -1 || pos > (sb.length() - 1);
        }

        @Override public String toString() {
            return sb.toString();
        }

    }

    static class History {
        public final boolean del;
        public final int pos;
        public final String str;
        private History(boolean del, int pos, String str) {
            this.del = del;
            this.pos = pos;
            this.str = str;
        }
        public static History insertOf(int pos, String str) {
            return new History(false, pos, str);
        }
        public static History deleteOf(int pos, String str) {
            return new History(true, pos, str);
        }
        public History inverse() {
            return new History(!del, pos, str);
        }
        public int getToPos() {
            return del ? pos : pos + str.length();
        }
    }

    // -- helper --------------------------------------------------------------

    private static List<String> split(String str, char c) {
        List<String> list = new ArrayList<>();
        int n = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.codePointAt(i) == c) {
                list.add(str.substring(n, i + 1));
                n = i + 1;
            }
        }
        if (n < str.length()) list.add(str.substring(n));
        return list;
    }

    private static String removeLf(String s) {
        return (s.charAt(s.length() - 1) == '\n') ? s.substring(0, s.length() - 1) : s;
    }

    private static File fileChooseOpen(Window owner) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select file...");
        fc.setInitialDirectory(new File(System.getProperty("user.home")));
        return fc.showOpenDialog(owner);
    }

    private static File fileChooseSave(Window owner) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save As...");
        fc.setInitialDirectory(new File(System.getProperty("user.home")));
        return fc.showSaveDialog(owner);
    }

    private static Text createText(String str, Paint color) {
        Text text = new Text();
        text.setFont(Settings.font);
        text.setTabSize(Settings.tabSize);
        text.setFill(color);
        text.setText(str);
        text.setLayoutY(text.getBaselineOffset());
        return text;
    }

    private static double getTextHeight(Text text) {
        return getHeight(text.rangeShape(0, 1));
    }

    private static double getTextHeight() {
        return getHeight(createText("XX", Settings.background).rangeShape(0, 1));
    }

    private static double getHeight(PathElement... elements) {
        return getPathMaxY(elements) - getPathMinY(elements);
    }

    private static double getPathMinX(PathElement... elements) {
        return Arrays.stream(elements).map(App::getPathX).min(Comparator.naturalOrder()).orElse(0.0);
    }

    private static double getPathMaxY(PathElement... elements) {
        return Arrays.stream(elements).map(App::getPathY).max(Comparator.naturalOrder()).orElse(0.0);
    }

    private static double getPathMinY(PathElement... elements) {
        return Arrays.stream(elements).map(App::getPathY).min(Comparator.naturalOrder()).orElse(0.0);
    }

    private static double getPathX(PathElement element) {
             if (element instanceof MoveTo) return ((MoveTo) element).getX();
        else if (element instanceof LineTo) return ((LineTo) element).getX();
        else throw new UnsupportedOperationException(element.toString());
    }

    private static double getPathY(PathElement element) {
             if (element instanceof MoveTo) return ((MoveTo) element).getY();
        else if (element instanceof LineTo) return ((LineTo) element).getY();
        else throw new UnsupportedOperationException(element.toString());
    }

    private static final Predicate<KeyEvent> controlKeysFilter = e ->
        System.getProperty("os.name").startsWith("Windows")
            ? e.isControlDown() && e.isAltDown() && !e.isMetaDown() &&
                e.getCharacter().length() == 1 && e.getCharacter().getBytes()[0] != 0
            : !e.isControlDown() && !e.isAltDown() && !e.isMetaDown();

    private static final Predicate<KeyEvent> isChar = e ->
            !e.getCode().isFunctionKey() && !e.getCode().isNavigationKey()
         && !e.getCode().isArrowKey() && !e.getCode().isModifierKey()
         && !e.getCode().isMediaKey() && controlKeysFilter.test(e);

    private static final KeyCombination SC_C = new KeyCharacterCombination("c", KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination SC_V = new KeyCharacterCombination("v", KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination SC_X = new KeyCharacterCombination("x", KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination SC_O = new KeyCharacterCombination("o", KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination SC_S = new KeyCharacterCombination("s", KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination SC_SA= new KeyCharacterCombination("s", KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
    private static final KeyCombination SC_Z = new KeyCharacterCombination("z", KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination SC_SZ= new KeyCharacterCombination("z", KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);

}