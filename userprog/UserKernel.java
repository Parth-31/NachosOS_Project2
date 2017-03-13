package nachos.userprog;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.TreeSet;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
	/**
	 * Allocate a new user kernel.
	 */
	public UserKernel() {
		super();
	}

	/**
	 * Initialize this kernel. Creates a synchronized console and sets the
	 * processor's exception handler.
	 */
	public void initialize(String[] args) {
		super.initialize(args);

		console = new SynchConsole(Machine.console());

		fileManager = new FileManager();
		processManager = new ProcessManager();

		Machine.processor().setExceptionHandler(new Runnable() {
			public void run() {
				exceptionHandler();
			}
		});
		
		initFreePages();
	}

	/**
	 * Test the console device.
	 */
	public void selfTest() {
		super.selfTest();

		System.out.println("Testing the console device. Typed characters");
		System.out.println("will be echoed until q is typed.");

		char c;

		do {
			c = (char) console.readByte(true);
			console.writeByte(c);
		} while (c != 'q');

		System.out.println("");
	}


	/**
	 * Returns the current process.
	 * 
	 * @return the current process, or <tt>null</tt> if no process is current.
	 */
	public static UserProcess currentProcess() {
		if (!(KThread.currentThread() instanceof UThread))
			return null;

		return ((UThread) KThread.currentThread()).process;
	}

	/**
	 * The exception handler. This handler is called by the processor whenever a
	 * user instruction causes a processor exception.
	 * 
	 * <p>
	 * When the exception handler is invoked, interrupts are enabled, and the
	 * processor's cause register contains an integer identifying the cause of
	 * the exception (see the <tt>exceptionZZZ</tt> constants in the
	 * <tt>Processor</tt> class). If the exception involves a bad virtual
	 * address (e.g. page fault, TLB miss, read-only, bus error, or address
	 * error), the processor's BadVAddr register identifies the virtual address
	 * that caused the exception.
	 */
	public void exceptionHandler() {
		Lib.assertTrue(KThread.currentThread() instanceof UThread);

		UserProcess process = ((UThread) KThread.currentThread()).process;
		int cause = Machine.processor().readRegister(Processor.regCause);
		process.handleException(cause);
	}

	/**
	 * Start running user programs, by creating a process and running a shell
	 * program in it. The name of the shell program it must run is returned by
	 * <tt>Machine.getShellProgramName()</tt>.
	 * 
	 * @see nachos.machine.Machine#getShellProgramName
	 */
	public void run() {
		super.run();

		UserProcess process = UserProcess.newUserProcess();

		String shellProgram = Machine.getShellProgramName();
		Lib.assertTrue(process.execute(shellProgram, new String[] {}));

		KThread.currentThread().finish();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}
	
	public class FileManager {
		public FileManager() {
			openList = new HashMap<String, FNode>();
		}
		
		private class FNode {
			FNode(String filename) {
				this.fname = filename;
				this.unlink = false;
				this.count = 1;
				
			}
			
			String fname;
			int count;
			boolean unlink;
		}
	
		public void openFile(String filename) {
			FNode fNode = openList.get(filename);
			if(fNode != null) {
				fNode.count++;
			}
			else {
				fNode = new FNode(filename);
				openList.put(filename, fNode);
			}
		}
		public boolean decrementCount(String filename) {
			FNode fNode = openList.get(filename);
			if(fNode == null) return false;
			fNode.count--;
			if(fNode.count == 0) {
				openList.remove(filename);
				if(fNode.unlink == true) {
					return fileSystem.remove(filename);
				}
			}
			return true;
		}
		
		public boolean unlink(String filename) {
			FNode fNode = (FNode)openList.get(filename);
			if(fNode == null) {
				return fileSystem.remove(filename);
			}
			fNode.unlink = true;
			return true;
		}
		
		public boolean isUnlink(String filename) {
			FNode fNode = openList.get(filename);
			if(fNode == null) return false;
			return fNode.unlink;
		}
		
		private HashMap<String, FNode> openList;
	}	

	public static UserKernel getKernel() {
		if(kernel instanceof UserKernel) return (UserKernel)kernel;
		return null;
	}
	
		private class PBlock implements Comparable<PBlock>{
			public int start;
			public int num_page;
			
			public PBlock(int s,int n){
				start = s;
				num_page = n;
			}
			
			public int compareTo(PBlock other){
				final int BEFORE = -1;
				final int EQUAL = 0;
				final int AFTER = 1;
				
				if(num_page < other.num_page)
					return BEFORE;
				if(num_page == other.num_page)
					return EQUAL;
				if(num_page > other.num_page)
					return AFTER;
				
				Lib.assertNotReached("Comparison Error");
				return EQUAL;
			}
		}
		private TreeSet<PBlock> freeBlocks = new TreeSet<PBlock>();         
		private LinkedList<PBlock> freePool = new LinkedList<PBlock>(); 
		private int sumFreePages = 0;
		private Semaphore freePage = null;
		
		private void initFreePages(){
			freePage = new Semaphore(1);
			
			freePage.P();
			
			sumFreePages = Machine.processor().getNumPhysPages();
			freeBlocks.clear();
			freeBlocks.add(new PBlock(0,sumFreePages));
			
			freePage.V();
		}
		
		public int getSmallestFreeBlock(){
			freePage.P();
			
			int res = 0;
			try{
				PBlock blk= freeBlocks.first();
				
				if(blk != null){
					res = blk.num_page;
				}
			}catch(NoSuchElementException e){
			}
			
			freePage.V();
			
			return res;
		}
		
		public int getLargestFreeBlock(){
			freePage.P();
			
			int res = 0;
			try{
				PBlock block= freeBlocks.last();
				
				if(block != null){
					res = block.num_page;
				}
			}catch(NoSuchElementException e){
			}
			
			freePage.V();
			
			return res;
		}
		public void releaseContiguousBlock(int s,int n){
			freePage.P(); 
			
			PBlock release = new PBlock(s,n);
			int postNbrOffset = release.start + release.num_page;
			int preNbrOffset = release.start;
			
			sumFreePages += release.num_page;
			ListIterator<PBlock> itr = freePool.listIterator();
			
			while(itr.hasNext()){
				PBlock cur = itr.next();
				
				if(cur.start < release.start){
					if(cur.start + cur.num_page == preNbrOffset){
						freeBlocks.remove(cur);
						cur.num_page += release.num_page;
						freeBlocks.add(cur);
						
						freePage.V();
						return;
					}
				}else{
					if(cur.start == postNbrOffset){
						freeBlocks.remove(cur);
						cur.num_page += release.num_page;
						cur.start = release.start;
						freeBlocks.add(cur);
						
						freePage.V();
						return;
					}
					
					itr.previous();
					break;
				}
			}
			
			itr.add(release);
			freeBlocks.add(release);
			
			freePage.V();
		}
		
		public int allocateContiguousBlock(int numPages){
			freePage.P();
			
			int result = -1;
			Iterator<PBlock> itr = freeBlocks.iterator();
			
			while(itr.hasNext()){
				PBlock curr = itr.next();
				
				if(curr.num_page >= numPages){
					result = curr.start;
					
					freeBlocks.remove(curr);
					freePool.remove(curr);
			
					sumFreePages -= curr.num_page;
					curr.start += numPages;
					curr.num_page -= numPages;
					
					if(curr.num_page > 0){
						freePage.V();
						releaseContiguousBlock(curr.start,curr.num_page);
						freePage.P();
					}
					
				}
			}
			
			freePage.V(); 
					
			return result;
		}
		
		public int getNumOfFreePages(){
			return sumFreePages;
		}
		
		public int allocate(){
			return allocateContiguousBlock(1);
		}
		
		public void release(int start){
			releaseContiguousBlock(start,1);
		}
		
		public TranslationEntry[] allocateTable(int numPages){
			TranslationEntry[] pageTable = new TranslationEntry[numPages];
			int currVirtualPage = 0;
			
			while(currVirtualPage < numPages){
				int neededPages = numPages - currVirtualPage;
				int start = -1;
				int largestBlock = getLargestFreeBlock();
				
				if(largestBlock <= 0){
					break;
				}else if(largestBlock < neededPages){
					neededPages = largestBlock;
				}

				start = allocateContiguousBlock(neededPages);
					
				if(start >= 0){ 
					for(int i=0;i<neededPages;i++){
						pageTable[i+currVirtualPage] = new TranslationEntry(i + currVirtualPage, i + start, true, false, false, false);
					}
					currVirtualPage += neededPages;
				}
			}
				
			if(pageTable[pageTable.length-1] == null){
				releaseTable(pageTable);
				
				pageTable = null;
			}
			
			return pageTable;
		}
		
		public void releaseTable(TranslationEntry[] pTable){
			PBlock blk = null;
			for(int currVirtualPage = 0; currVirtualPage < pTable.length; currVirtualPage++){
				TranslationEntry transentry = pTable[currVirtualPage];
				if(transentry!=null && transentry.valid){
					if(blk!=null){
						if((blk.start + blk.num_page) == transentry.ppn){
							blk.num_page += 1;
						}else{
							releaseContiguousBlock(blk.start,blk.num_page);
							blk.num_page = 1;
							blk.start = transentry.ppn;
						}
					}else{
					  blk = new PBlock(transentry.ppn, 1);
					}
				}
			}
			if(blk!=null){
				releaseContiguousBlock(blk.start,blk.num_page);
			}
		}
		public class ProcessManager {
			public ProcessManager() {
				procList = new TreeMap<Integer, ProcessNode>();
			}
			public boolean exists(int pID) {
				return procList.containsKey(pID);
			}
			public int newProcess(UserProcess proc, int parent) {
				ProcessNode newProcessNode = new ProcessNode(proc, parent, nextProcessID);
				procList.put(newProcessNode.pid, newProcessNode);
				nextProcessID++;
				return newProcessNode.pid;
			}
			public UserProcess getProcess(int pID) {
				return procList.get(pID).process;
			}
			public void parentChange(int childPID, int parentPID) {
				procList.get(childPID).parent = parentPID;
			}
			
			public boolean checkNoChildren(int parentID) {
				Iterator iterator = procList.keySet().iterator();
				ProcessNode processNode;
				while(iterator.hasNext()) {
					processNode = procList.get(iterator.next());
					if(processNode.parent == parentID) {
						return false;
					}
				}
				return true;
			}
			
			public void remParent(int parentPID) {
				Iterator iter = procList.keySet().iterator();
				ProcessNode processNode;
				while(iter.hasNext()) {
					processNode = procList.get(iter.next());
					if(processNode.parent == parentPID) {
						parentChange(processNode.pid, -1);
					}
				}
			}
			public int getParent(int cPID) {
				return procList.get(cPID).parent;
			}
			
			public void setReturnCode(int pid, int retCode) {
				procList.get(pid).exitStatus = retCode;
			}
			
			public boolean checkforRunning(int processID) {
				return procList.get(processID).running;
			}
			
			public void setFinish(int processID) {
				procList.get(processID).running = false;
			}
			
			public int getReturnCode(int pID) {
				ProcessNode proc = procList.get(pID);
				return proc.exitStatus;
			}
			
			public boolean checkError(int pID) {
				return procList.get(pID).error;
			}
			
			public void setError(int pID) {
				procList.get(pID).error = true;
			}
			
			public boolean isLast(int pID) {
				Iterator iterate = procList.keySet().iterator();
				ProcessNode processNode;
				int count = 0;
				while(iterate.hasNext()) {
					processNode = procList.get(iterate.next());
					if(processNode.pid != pID && processNode.running) {
						count++;
					}
				}
				return (count == 0);
			}
			
			private class ProcessNode {
				public ProcessNode(UserProcess process, int parent, int pid) {
					this.pid = pid;
					this.parent = parent;
					this.process = process;
					this.running = true;
					this.joined = false;
					this.error = false;
				}
				
				int pid;
				int parent;
				int exitStatus;
				boolean joined;
				boolean running;
				boolean error;
				UserProcess process;
			}
			
			private int nextProcessID = 0;
			private TreeMap<Integer, ProcessNode> procList;
		}


	public ProcessManager processManager;



	public FileManager fileManager;


	/** Globally accessible reference to the synchronized console. */
	public static SynchConsole console;

	// dummy variables to make javac smarter
	private static Coff dummy1 = null;
}
