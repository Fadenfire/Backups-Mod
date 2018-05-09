package silly511.backups.util;

import java.io.IOException;

@FunctionalInterface
public interface IORunnable {
	
	void run() throws IOException;

}
