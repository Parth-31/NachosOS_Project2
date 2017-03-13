package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
	/**
	 * Allocate a new communicator.
	 */

	public Communicator() 
	{
		lock = new Lock();
		speaker = new Condition2Queue(lock);
		listener = new Condition2Queue(lock);
		transfer = new Condition2Queue(lock);
		data = 0;
		flag=false;
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 * 
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 * 
	 * @param word the integer to transfer.
	 */


	public void speak(int word) 
	{
		lock.acquire();
		if (listener.empty() || flag) //while word is in use
			speaker.sleep(); //put this speaker in send queue

		flag = true; //shared word in use
		data = word; //transfer word
		listener.wake();
		transfer.sleep();
		flag = false;
		if (!speaker.empty() && !listener.empty()) 
		{
			flag = true;
			speaker.wake();
		}
		lock.release();

	}

	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 * 
	 * @return the integer transferred.
	 */
	public int listen() 
	{
		lock.acquire();
		if (!flag && !speaker.empty()) 
		{
			flag = true;
			speaker.wake();
		}
		listener.sleep(); //put listener in queue
		int msg = data;
		transfer.wake();
		lock.release();
		return (msg);
	}

	/**
	 * Tests whether this module is working.
	 */
	public static void selfTest() 
	{
		System.out.println("------------------------------------------------------------------");

		System.out.println("Communicator Self Test");


	}

	int data;
	boolean flag;

	Lock lock;
	Condition2Queue speaker, listener, transfer;

	class Condition2Queue {
		Condition2 cond;
		int count;

		Condition2Queue(Lock lock) {
			count = 0;
			cond = new Condition2(lock);
		}

		boolean empty() {
			return (count == 0);
		}

		void sleep() {
			count++;
			cond.sleep();
		}

		void wake() {
			count--;
			cond.wake();
		}



	}
}
