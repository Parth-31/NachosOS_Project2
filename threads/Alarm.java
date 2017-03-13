package nachos.threads;

import java.util.PriorityQueue;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		boolean intStatus = Machine.interrupt().disable(); //atomic operation

		long start_time = Machine.timer().getTime();
		while (!wait_queue.isEmpty() && start_time > wait_queue.peek().wake_time) //As long as queue isn't empty and the waiting period has not completed yet 
			wait_queue.poll().thread.ready();

		KThread.yield();

		Machine.interrupt().restore(intStatus);

	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		
		boolean interruptStatus = Machine.interrupt().disable();
		
		//System.out.println("Current time = " + Machine.timer().getTime());
		
		long wake_time = Machine.timer().getTime() + x; //calculate wake time
		
		//System.out.println("Wait Until wake time = " + wake_time);
		
		wait_queue.add(new Pair(wake_time, KThread.currentThread()));
		 
		KThread.sleep();
			
		Machine.interrupt().restore(interruptStatus);
	}

	PriorityQueue<Pair> wait_queue = new PriorityQueue<Pair>();

	class Pair implements Comparable<Pair> {
		long wake_time;
		KThread thread;

		Pair(long _wakeTime, KThread _waitingThread) {
			wake_time = _wakeTime;
			thread = _waitingThread;
		}

		public int compareTo(Pair pair) {
		if(wake_time < pair.wake_time)
			return -1;
		else if(wake_time > pair.wake_time)
			return 1;
		else return 0;
			
		}
	}
	
	public static void selfTest() 
	{
		Lib.debug(dbgThread, "Enter Alarm.selfTest");

		System.out.println("------------------------------------------------------------------");
		
		System.out.println("Alarm.selfTest()");
		
	}
	private static final char dbgThread = 'a';
}
