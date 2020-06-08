package simpledb;

import java.io.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BufferPool manages the reading and writing of pages into memory from
 * disk. Access methods call into it to retrieve pages, and it fetches
 * pages from the appropriate location.
 * <p>
 * The BufferPool is also responsible for locking;  when a transaction fetches
 * a page, BufferPool checks that the transaction has the appropriate
 * locks to read/write the page.
 *
 * @Threadsafe, all fields are final
 */
public class BufferPool {
    /** Bytes per page, including header. */
    private static final int PAGE_SIZE = 4096;

    private static int pageSize = PAGE_SIZE;

    /** Default number of pages passed to the constructor. This is used by
    other classes. BufferPool should use the numPages argument to the
    constructor instead. */
    public static final int DEFAULT_PAGES = 50;

    /**
     * Creates a BufferPool that caches up to numPages pages.
     *
     * @param numPages maximum number of pages in this buffer pool.
     */

    private final int numPages;
    //private Map<PageId, Integer> LRU;
    private ConcurrentHashMap<PageId, Page> cache;

    /*private void updateLRU(PageId pid){
        LRU.put(pid, numPages+1);
        LRU.replaceAll((k,v)->v==0?0:v-1);
    }*/
    private ConcurrentHashMap<PageId, PLock> pLockMap;
    private ConcurrentHashMap<TransactionId, Set<PageId>> tLockMap;
    private DGraph dGraph;

    private class PLock {
        private PageId pid;
        private Set<TransactionId> sLock;
        private TransactionId eLock = null;

        PLock(PageId pid){
            this.pid = pid;
            this.sLock = ConcurrentHashMap.newKeySet();
        }

        boolean requestLock(Permissions perm, TransactionId tid){
            if (perm.equals(Permissions.READ_ONLY)){
                if (eLock != null)
                    return eLock.equals(tid);
                sLock.add(tid);
                return true;
            }
            if (perm.equals(Permissions.READ_WRITE)){
                if (eLock != null)
                    return eLock.equals(tid);
                if (sLock.size() > 1)
                    return false;
                if (sLock.isEmpty() || sLock.contains(tid)){
                    eLock = tid;
                    sLock.clear();
                    return true;
                }
                return false;
            }
            throw new Error("what is the fucking permission when request a lock");
        }

        void releaseLock(TransactionId tid){
            if (tid.equals(eLock))
                eLock = null;
            else sLock.remove(tid);
        }

        boolean holdsLock(TransactionId tid){
            return tid.equals(eLock) || sLock.contains(tid);
        }

        boolean isExclusive(){
            return eLock != null;
        }

        Set<TransactionId> lockedTids() {
            Set<TransactionId> tid = new HashSet<>(sLock);
            if (eLock != null)
                tid.add(eLock);
            return tid;
        }
    }

    //help to check if there is a dead lock
    private class DGraph{
        private ConcurrentHashMap<TransactionId, HashSet<TransactionId>> edges = new ConcurrentHashMap<>();

        synchronized void update(TransactionId tid, PageId pid){
            edges.putIfAbsent(tid, new HashSet<>());
            HashSet<TransactionId> edge = edges.get(tid);
            edge.clear();
            if (pid == null)
                return;
            Set<TransactionId> lockedT;
            synchronized (pLockMap.get(pid)){
                lockedT = pLockMap.get(pid).lockedTids();
            }
            edge.addAll(lockedT);
        }

        synchronized boolean hasCycle(TransactionId tid){
            Queue<TransactionId> queue = new LinkedList<>();
            HashSet<TransactionId> visited = new HashSet<>();
            queue.add(tid);
            //bfs
            while (!queue.isEmpty()){
                TransactionId cur = queue.poll();
                HashSet<TransactionId> neighbors = edges.get(cur);
                if (neighbors == null)
                    continue;
                for (TransactionId t : neighbors){
                    if (!visited.contains(t)){
                        queue.add(t);
                        visited.add(t);
                    }
                    if (t.equals(tid))
                        break;
                }
            }
            return visited.contains(tid);
        }

    }

    public BufferPool(int numPages) {
        // some code goes here
        this.numPages = numPages;
        //this.LRU = new HashMap<>();
        this.cache = new ConcurrentHashMap<>();
        this.pLockMap = new ConcurrentHashMap<>();
        this.tLockMap = new ConcurrentHashMap<>();
        this.dGraph = new DGraph();
    }

    public static int getPageSize() {
      return pageSize;
    }

    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void setPageSize(int pageSize) {
    	BufferPool.pageSize = pageSize;
    }

    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void resetPageSize() {
    	BufferPool.pageSize = PAGE_SIZE;
    }

    /**
     * Retrieve the specified page with the associated permissions.
     * Will acquire a lock and may block if that lock is held by another
     * transaction.
     * <p>
     * The retrieved page should be looked up in the buffer pool.  If it
     * is present, it should be returned.  If it is not present, it should
     * be added to the buffer pool and returned.  If there is insufficient
     * space in the buffer pool, an page should be evicted and the new page
     * should be added in its place.
     *
     * @param tid the ID of the transaction requesting the page
     * @param pid the ID of the requested page
     * @param perm the requested permissions on the page
     */


    public  Page getPage(TransactionId tid, PageId pid, Permissions perm)
        throws TransactionAbortedException, DbException {
        // some code goes here
        pLockMap.putIfAbsent(pid, new PLock(pid));
        boolean requestLock;
        synchronized (pLockMap.get(pid)){
            requestLock = pLockMap.get(pid).requestLock(perm, tid);
        }

        while (!requestLock){
            Thread.yield();
            dGraph.update(tid, pid);
            if (dGraph.hasCycle(tid))
                throw new TransactionAbortedException();
            Thread.yield();
            synchronized (pLockMap.get(pid)){
                requestLock = pLockMap.get(pid).requestLock(perm, tid);
            }
        }
        dGraph.update(tid, null);
        tLockMap.putIfAbsent(tid, new HashSet<>());
        tLockMap.get(tid).add(pid);

        if (cache.containsKey(pid))
            return cache.get(pid);

        while (cache.size() >= numPages)
            evictPage();
        Page page = Database.getCatalog().getDatabaseFile(pid.getTableId()).readPage(pid);
        cache.put(pid, page);
        page.setBeforeImage();
        return page;
    }

    /**
     * Releases the lock on a page.
     * Calling this is very risky, and may result in wrong behavior. Think hard
     * about who needs to call this and why, and why they can run the risk of
     * calling it.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param pid the ID of the page to unlock
     */
    public  void releasePage(TransactionId tid, PageId pid) {
        // some code goes here
        // not necessary for lab1|lab2
        synchronized (pLockMap.get(pid)){
            pLockMap.get(pid).releaseLock(tid);
        }
        tLockMap.get(tid).remove(pid);
    }

    /**
     * Release all locks associated with a given transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     */
    public void transactionComplete(TransactionId tid) throws IOException {
        // some code goes here
        // not necessary for lab1|lab2
        transactionComplete(tid, true);
    }

    /** Return true if the specified transaction has a lock on the specified page */
    public boolean holdsLock(TransactionId tid, PageId p) {
        // some code goes here
        // not necessary for lab1|lab2
        boolean holdsLock;
        synchronized (pLockMap.get(p)){
            holdsLock = pLockMap.get(p).holdsLock(tid);
        }
        return holdsLock;
    }

    /**
     * Commit or abort a given transaction; release all locks associated to
     * the transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param commit a flag indicating whether we should commit or abort
     */
    public void transactionComplete(TransactionId tid, boolean commit)
        throws IOException {
        // some code goes here
        // not necessary for lab1|lab2
        Set<PageId> locked = tLockMap.get(tid);
        tLockMap.remove(tid);
        if (locked == null)
            return;
        for (PageId pid : locked){
            Page p = cache.get(pid);
            if (p != null && pLockMap.get(pid).isExclusive()){
                if (commit) {
                    if (p.isDirty() == null)
                        continue;
                    flushPage(pid);
                    p.setBeforeImage();
                }
                else
                    cache.put(pid, p.getBeforeImage());
            }
            synchronized (pLockMap.get(pid)) {
                pLockMap.get(pid).releaseLock(tid);
            }
        }
    }

    /**
     * Add a tuple to the specified table on behalf of transaction tid.  Will
     * acquire a write lock on the page the tuple is added to and any other
     * pages that are updated (Lock acquisition is not needed for lab2).
     * May block if the lock(s) cannot be acquired.
     *
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have
     * been dirtied to the cache (replacing any existing versions of those pages) so
     * that future requests see up-to-date pages.
     *
     * @param tid the transaction adding the tuple
     * @param tableId the table to add the tuple to
     * @param t the tuple to add
     */
    public void insertTuple(TransactionId tid, int tableId, Tuple t)
        throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        // not necessary for lab1
        ArrayList<Page> pageList = Database.getCatalog().getDatabaseFile(tableId).insertTuple(tid, t);
        for (Page page : pageList){
            PageId pid = page.getId();
            if (!cache.containsKey(pid)){
                while (cache.size() >= numPages)
                    evictPage();
                cache.put(pid, page);
            }
            page.markDirty(true, tid);
            //updateLRU(pid);
        }
    }

    /**
     * Remove the specified tuple from the buffer pool.
     * Will acquire a write lock on the page the tuple is removed from and any
     * other pages that are updated. May block if the lock(s) cannot be acquired.
     *
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have
     * been dirtied to the cache (replacing any existing versions of those pages) so
     * that future requests see up-to-date pages.
     *
     * @param tid the transaction deleting the tuple.
     * @param t the tuple to delete
     */
    public  void deleteTuple(TransactionId tid, Tuple t)
        throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        // not necessary for lab1
        int tableId = t.getRecordId().getPageId().getTableId();
        ArrayList<Page> pageList = Database.getCatalog().getDatabaseFile(tableId).deleteTuple(tid, t);
        for (Page page : pageList){
            PageId pid = page.getId();
            if (!cache.containsKey(pid)){
                while (cache.size() >= numPages)
                    evictPage();
                cache.put(pid, page);
            }
            page.markDirty(true, tid);
            //updateLRU(pid);
        }
    }

    /**
     * Flush all dirty pages to disk.
     * NB: Be careful using this routine -- it writes dirty data to disk so will
     *     break simpledb if running in NO STEAL mode.
     */
    public synchronized void flushAllPages() throws IOException {
        // some code goes here
        // not necessary for lab1
        for (PageId pid : cache.keySet())
            flushPage(pid);
    }

    /** Remove the specific page id from the buffer pool.
        Needed by the recovery manager to ensure that the
        buffer pool doesn't keep a rolled back page in its
        cache.

        Also used by B+ tree files to ensure that deleted pages
        are removed from the cache so they can be reused safely
    */
    public synchronized void discardPage(PageId pid) {
        // some code goes here
        // not necessary for lab1
        if (!cache.containsKey(pid))
            return;
        /*try {
            flushPage(pid);
        } catch (IOException e){
            e.printStackTrace();
        }*/
        cache.remove(pid);
        //LRU.remove(pid);
    }

    /**
     * Flushes a certain page to disk
     * @param pid an ID indicating the page to flush
     */
    private synchronized  void flushPage(PageId pid) throws IOException {
        // some code goes here
        // not necessary for lab1
        Page page = cache.get(pid);
        if (page == null)
            throw new IOException();
        if (page.isDirty() == null)
            return;
        page.markDirty(false, null);
        Database.getCatalog().getDatabaseFile(pid.getTableId()).writePage(page);
    }

    /** Write all pages of the specified transaction to disk.
     */
    public synchronized  void flushPages(TransactionId tid) throws IOException {
        // some code goes here
        // not necessary for lab1|lab2
    }

    /**
     * Discards a page from the buffer pool.
     * Flushes the page to disk to ensure dirty pages are updated on disk.
     */
    private synchronized  void evictPage() throws DbException {
        // some code goes here
        // not necessary for lab1
        /*boolean flag = true;
        Map.Entry<PageId, Integer> lru = null;
        for (Map.Entry<PageId, Integer> cur : LRU.entrySet()){
            if (flag){
                lru = cur;
                flag = false;
            }
            else if (lru.getValue() > cur.getValue())
                lru = cur;
        }
        PageId pid = lru.getKey();
        try {
            flushPage(pid);
        } catch (IOException e){
            e.printStackTrace();
        }
        cache.remove(pid);
        LRU.remove(pid);*/
        for (Map.Entry<PageId, Page> e : cache.entrySet()){
            PageId pid = e.getKey();
            synchronized (cache.get(pid)) {
                if (cache.get(pid).isDirty() == null){
                    try {
                        flushPage(pid);
                    }
                    catch (IOException err){
                        err.printStackTrace();
                    }
                    cache.remove(pid);
                    return;
                }
            }
        }
        throw new DbException("NO STEAL Policy fail");
    }

}
