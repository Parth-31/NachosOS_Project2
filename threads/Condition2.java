package nachos.threads;

import nachos.machine.*;


/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 * 
 * <p>
 * You must implement this.
 * 
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		intState = Machine.interrupt().disable(); //for atomic operation
		conditionLock.release();
		waiting_queue.waitForAccess(KThread.currentThread());
		KThread.sleep();
		conditionLock.acquire();
		Machine.interrupt().restore(intState);  //end of atomic operation
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		
		intState = Machine.interrupt().disable(); //for atomic operation
		KThread t = waiting_queue.nextThread(); //get the next thread from the queue
		if (t != null)
			t.ready(); //Move this thread to ready queue
		Machine.interrupt().restore(intState); // end of atomic execution
		
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() //similar to wake() but this loops through all the threads in the queue
	{
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		intState = Machine.interrupt().disable();
		KThread thread = waiting_queue.nextThread();
		while (thread != null) //iterate through all threads in the queue
		{
			thread.ready();
			thread = waiting_queue.nextThread();
		}
		Machine.interrupt().restore(intState);
	}

	public static void selfTest() 
	{
		Lib.debug(dbgThread, "Enter Condition2.selfTest");

		System.out.println("------------------------------------------------------------------");
		
		System.out.println("Condition2.selfTest()");
		
	}

	

	private static final char dbgThread = 't';

	private boolean intState;
	private Lock conditionLock;
	private ThreadQueue waiting_queue= ThreadedKernel.scheduler.newThreadQueue(false); // no priority transfer => false
}