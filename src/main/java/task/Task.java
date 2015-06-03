package task;

/**
 * Task interface provides process with String array arguments
 */
public interface Task
{
	void process( final String[] args );

	static boolean isDebug = false;
}
