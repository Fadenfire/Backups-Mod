package silly511.backups.util;

public abstract class PollableThread extends Thread {
	
	private boolean done;
	private Exception exception;
	
	public abstract void doTask() throws Exception;
	
	@Override
	public void run() {
		try {
			doTask();
		} catch (Exception ex) {
			exception = ex;
		} finally {
			done = true;
		}
	}
	
	public boolean isDone() {
		return done;
	}
	
	public boolean errored() {
		return exception != null;
	}
	
	public Exception getException() {
		return exception;
	}

}
