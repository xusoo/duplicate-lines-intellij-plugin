import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;

/**
 * Text editor action that duplicates the entire selected lines up or down, moving the caret with the duplicated text.
 */
public class DuplicateLines extends AnAction {

	private enum Mode {
		DUPLICATE_UP, DUPLICATE_DOWN
	}

	@Override
	public void actionPerformed(AnActionEvent event) {

		/* Up or down? */
		Mode mode = event.getActionManager().getId(this).equals("DuplicateLinesUp") ? Mode.DUPLICATE_UP : Mode.DUPLICATE_DOWN;

		Editor editor = event.getData(CommonDataKeys.EDITOR);

		if (editor == null) {
			/* Shouldn't be possible as the update() method takes care of it, but just in case */
			return;
		}

		CaretModel caretModel = editor.getCaretModel();

		/* Execute the same action for each caret. And we wrap them all in the same command so we can undo them all at once */
		WriteCommandAction.runWriteCommandAction(event.getProject(), () -> {
			caretModel.runForEachCaret(caret -> duplicateLines(caret, mode));
		});
	}

	/**
	 * Only enabled and visible if there is an active editor
	 */
	@Override
	public void update(AnActionEvent event) {
		event.getPresentation().setEnabledAndVisible(event.getData(CommonDataKeys.EDITOR) != null);
	}

	/**
	 * Prepares the caret selection and duplicates the line (up or down depeding of the <code>mode</code> parameter)
	 */
	private void duplicateLines(Caret caret, Mode mode) {

		Editor editor = caret.getEditor();
		Document document = editor.getDocument();

		/* Start and end selection points */
		int selectionStart = caret.getSelectionStart();
		int selectionEnd = caret.getSelectionEnd();

		/* Starting and ending line numbers (We need the logical positions which are aware of the soft wraps and code folds) */
		int selectionStartLine = editor.offsetToLogicalPosition(selectionStart).line;
		int selectionEndLine = editor.offsetToLogicalPosition(selectionEnd).line;

		/*
		 * We extend the selection to the start of the first selected line and to the end of the last one in order
		 * to grab the entire lines and not just the selected text (if any)
		 */
		int lineStartOffset = document.getLineStartOffset(selectionStartLine);
		int lineEndOffset = document.getLineEndOffset(selectionEndLine);
		caret.setSelection(lineStartOffset, lineEndOffset);

		String selectedText = caret.getSelectedText() != null ? caret.getSelectedText() : "";

		if (mode == Mode.DUPLICATE_UP) {
			duplicateUp(caret, document, selectionStart, selectionEnd, lineStartOffset, selectedText);
		} else {
			duplicateDown(caret, document, selectionStart, selectionEnd, lineEndOffset, selectedText);
		}

		editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
	}

	/**
	 * If we are duplicating up, the new code will be at the same exact position as the original one, so we need to
	 * preserve the same offsets and caret position.
	 */
	private void duplicateUp(Caret caret, Document document, int selectionStart, int selectionEnd, int insertPoint, String selectedText) {
		int caretPosition = caret.getOffset();
		document.insertString(insertPoint, selectedText + "\n");
		caret.setSelection(selectionStart, selectionEnd);
		caret.moveToOffset(caretPosition);
	}

	/**
	 * But if we are duplicating down, all the offsets must be increased by the length of the duplicated text.
	 */
	private void duplicateDown(Caret caret, Document document, int selectionStart, int selectionEnd, int insertPoint, String selectedText) {
		int caretPosition = caret.getOffset();
		String text = "\n" + selectedText;
		document.insertString(insertPoint, text);
		caret.setSelection(selectionStart + text.length(), selectionEnd + text.length());
		caret.moveToOffset(caretPosition + text.length());
	}
}
