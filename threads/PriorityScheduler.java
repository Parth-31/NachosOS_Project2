package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Comparator;

/**
 * A scheduler that chooses threads based on their priorities.
 * 
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the thread
 * that has been waiting longest.
 * 
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has the
 * potential to starve a thread if there's always a thread waiting with higher
 * priority.
 * 
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 * 
	 * @param transferPriority <tt>true</tt> if this queue should transfer
	 * priority from waiting threads to the owning thread.
	 * @return a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum
				&& priority <= priorityMaximum);
		
		ThreadState threadState = getThreadState(thread);
		
		if(priority != threadState.getPriority())
			threadState.setPriority(priority);
	
	}

	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();
		boolean ret = true;

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			ret = false;
		else
			setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return ret;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();
		boolean ret = true;

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			ret = false;
		else
			setPriority(thread, priority - 1);

		Machine.interrupt().restore(intStatus);
		return ret;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;

	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;

	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;

	/**
	 * Return the scheduling state of the specified thread.
	 * 
	 * @param thread the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class PriorityQueue extends ThreadQueue {
		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me		
			if(waitQueue.isEmpty()){
				return null;
			}else{
				acquire(waitQueue.poll().thread);
				return lockingThread;
			}	
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 * 
		 * @return the next thread that <tt>nextThread()</tt> would return.
		 */
		protected ThreadState pickNextThread() {
			
			return waitQueue.peek();
		}

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;
		
		private java.util.PriorityQueue<ThreadState> waitQueue = new java.util.PriorityQueue<ThreadState>(8,new ThreadStateComparator<ThreadState>(this));
//KThread locks PQueue and initially it is null.		
		private KThread lockingThread = null;	
		
		protected class ThreadStateComparator<T extends ThreadState> implements Comparator<T>{
			protected ThreadStateComparator(nachos.threads.PriorityScheduler.PriorityQueue pq){
				priorityQueue = pq;
			}
			
			@Override
			public int compare(T o1, T o2){
				//compare them by their effective priority
				if(o1.getEffectivePriority() > o2.getEffectivePriority()) return -1;
				else if(o1.getEffectivePriority() < o2.getEffectivePriority()) return 1;
				else{
				//if the priority is same then compare them by their wait time
					if(o1.waiting.get(priorityQueue) < o2.waiting.get(priorityQueue)) return -1;
					else if(o1.waiting.get(priorityQueue) > o2.waiting.get(priorityQueue)) return 1;
					else return 0; //probably the same thread;
				}
			}
			
			private nachos.threads.PriorityScheduler.PriorityQueue priorityQueue;
		}
	}
	
		

	/**
	 * The scheduling state of a thread. This should include the thread's
	 * priority, its effective priority, any objects it owns, and the queue it's
	 * waiting for, if any.
	 * 
	 * @see nachos.threads.KThread#schedulingState
	 */
	protected class ThreadState {
		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 * 
		 * @param thread the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;
			
			effectivePriority = priorityDefault;
			setPriority(priorityDefault);
		}
		
		private void release(PriorityQueue priorityQueue){
			if(acquired.remove(priorityQueue)){
				priorityQueue.lockingThread = null;
				updateEffectivePriority();
			}
		}

		/**
		 * Return the priority of the associated thread.
		 * 
		 * @return the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 * 
		 * @return the effective priority of the associated thread.
		 */
		public int getEffectivePriority() {
			// implement me
			
			return effectivePriority;

		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 * 
		 * @param priority the new priority.
		 */
		public void setPriority(int priority) {
			// implement me
			this.priority = priority;
			updateEffectivePriority();
		}
		
		protected void updateEffectivePriority() {
	
			for (PriorityQueue pq : waiting.keySet())
				pq.waitQueue.remove(this);
			
			int tempPriority = priority;
			
			for (PriorityQueue pq : acquired) {
				if (pq.transferPriority) {
					ThreadState topTS = pq.waitQueue.peek();
					if (topTS != null) {
						int topPQ_AP = topTS.getEffectivePriority();
						
						if (topPQ_AP > tempPriority)
							tempPriority = topPQ_AP;
					}
				}
			}
			
			boolean needToTransfer = tempPriority != effectivePriority;
			
			effectivePriority = tempPriority;
			
			/*
			 * Add this back in and propagate up all the results
			 */		
			for (PriorityQueue pq : waiting.keySet())
				pq.waitQueue.add(this);

			if (needToTransfer){
				for (PriorityQueue pq : waiting.keySet()) {
					if (pq.transferPriority && pq.lockingThread != null)
						getThreadState(pq.lockingThread).updateEffectivePriority();
				}
			}
		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		 * the associated thread) is invoked on the specified priority queue.
		 * The associated thread is therefore waiting for access to the resource
		 * guarded by <tt>waitQueue</tt>. This method is only called if the
		 * associated thread cannot immediately obtain access.
		 * 
		 * @param waitQueue the queue that the associated thread is now waiting
		 * on.
		 * 
		 * @see nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityQueue waitQueue) {
			// implement me

			if (!waiting.containsKey(waitQueue)) {
				//Unlock this wait queue, if THIS holds it
				release(waitQueue);
				
				//Put it on the queue
				waiting.put(waitQueue, Machine.timer().getTime());
				
				//The effective priority of this shouldn't change, so just shove it onto the waitQueue's members
				waitQueue.waitQueue.add(this);
				
				if (waitQueue.lockingThread != null) {
					getThreadState(waitQueue.lockingThread).updateEffectivePriority();
				}
			}
			
		}

		/**
		 * Called when the associated thread has acquired access to whatever is
		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 * 
		 * @see nachos.threads.ThreadQueue#acquire
		 * @see nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityQueue waitQueue) {
			// implement me
		
			//Unlock the current locking thread
			if (waitQueue.lockingThread != null) {
				getThreadState(waitQueue.lockingThread).release(waitQueue);
			}
			
			/*
			 * Remove the passed thread state from the queues, if it exists on them
			 */
			waitQueue.waitQueue.remove(this);
			
			//Acquire the thread
			waitQueue.lockingThread = this.thread;
			acquired.add(waitQueue);
			waiting.remove(waitQueue);
			
			updateEffectivePriority();
		}

		/** The thread with which this object is associated. */
		protected KThread thread;

		/** The priority of the associated thread. */
		protected int priority;
		
		protected int effectivePriority;
		
		/** A set of all the PriorityQueues this ThreadState has acquired */
		private HashSet<nachos.threads.PriorityScheduler.PriorityQueue> acquired = new HashSet<nachos.threads.PriorityScheduler.PriorityQueue>();
		
		/** A map of all the PriorityQueues this ThreadState is waiting on mapped to the time they were waiting on them*/
		HashMap<nachos.threads.PriorityScheduler.PriorityQueue,Long> waiting = new HashMap<nachos.threads.PriorityScheduler.PriorityQueue,Long>();
	
	}
	
	public static void selfTest() {
		
		System.out.println("------------------------------------------------------------------");
		System.out.println("PriorityScheduler.selfTest()");
		
		
	}
}

