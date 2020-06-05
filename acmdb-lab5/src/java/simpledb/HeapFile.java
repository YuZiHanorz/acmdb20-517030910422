package simpledb;

import java.io.*;
import java.util.*;

/**
 * HeapFile is an implementation of a DbFile that stores a collection of tuples
 * in no particular order. Tuples are stored on pages, each of which is a fixed
 * size, and the file is simply a collection of those pages. HeapFile works
 * closely with HeapPage. The format of HeapPages is described in the HeapPage
 * constructor.
 *
 * @see simpledb.HeapPage#HeapPage
 * @author Sam Madden
 */
public class HeapFile implements DbFile {

    /**
     * Constructs a heap file backed by the specified file.
     *
     * @param f
     *            the file that stores the on-disk backing store for this heap
     *            file.
     */
    private File f;
    private TupleDesc td;

    public HeapFile(File f, TupleDesc td) {
        // some code goes here
        this.f = f;
        this.td = td;
    }

    /**
     * Returns the File backing this HeapFile on disk.
     *
     * @return the File backing this HeapFile on disk.
     */
    public File getFile() {
        // some code goes here
        return f;
    }

    /**
     * Returns an ID uniquely identifying this HeapFile. Implementation note:
     * you will need to generate this tableid somewhere ensure that each
     * HeapFile has a "unique id," and that you always return the same value for
     * a particular HeapFile. We suggest hashing the absolute file name of the
     * file underlying the heapfile, i.e. f.getAbsoluteFile().hashCode().
     *
     * @return an ID uniquely identifying this HeapFile.
     */
    public int getId() {
        // some code goes here
        return f.getAbsoluteFile().hashCode();
    }

    /**
     * Returns the TupleDesc of the table stored in this DbFile.
     *
     * @return TupleDesc of this DbFile.
     */
    public TupleDesc getTupleDesc() {
        // some code goes here
        return td;
    }

    // see DbFile.java for javadocs
    public Page readPage(PageId pid) {
        // some code goes here
        int pgSize = BufferPool.getPageSize();
        int offset = pid.pageNumber() * pgSize;
        if (offset + pgSize > f.length())
            throw new IllegalArgumentException();

        byte[] bytes = new byte[pgSize];
        HeapPage pg;
        RandomAccessFile raf;
        try{
            raf = new RandomAccessFile(f, "r");
            raf.seek(offset);
            raf.readFully(bytes);
            pg = new HeapPage((HeapPageId)pid, bytes);
        } catch (IOException e){
            throw new IllegalArgumentException();
        }
        return pg;
    }

    // see DbFile.java for javadocs
    public void writePage(Page page) throws IOException {
        // some code goes here
        // not necessary for lab1
        int offset = page.getId().pageNumber() * BufferPool.getPageSize();
        RandomAccessFile file;
        try {
            file = new RandomAccessFile(f, "rw");
            file.seek(offset);
            file.write(page.getPageData());
        }
        catch (IOException e){
            throw new IOException("fail to write page");
        }

    }

    /**
     * Returns the number of pages in this HeapFile.
     */
    public int numPages() {
        // some code goes here
        return (int)(f.length() / BufferPool.getPageSize());
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> insertTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        // not necessary for lab1
        for (int i = 0; i < numPages(); ++i){
            HeapPageId pid = new HeapPageId(getId(), i);
            HeapPage p = (HeapPage) Database.getBufferPool().getPage(tid, pid, Permissions.READ_ONLY);
            if (p.getNumEmptySlots() == 0) {
                Database.getBufferPool().releasePage(tid, pid);
                continue;
            }
            p = (HeapPage) Database.getBufferPool().getPage(tid, pid, Permissions.READ_WRITE);
            p.insertTuple(t);
            return new ArrayList<>(Collections.singletonList(p));
        }
        HeapPageId pid = new HeapPageId(getId(), numPages());
        HeapPage p = new HeapPage(pid, HeapPage.createEmptyPageData());
        p.insertTuple(t);
        writePage(p);
        return new ArrayList<>(Collections.singletonList(p));
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> deleteTuple(TransactionId tid, Tuple t) throws DbException,
            TransactionAbortedException {
        // some code goes here
        // not necessary for lab1
        PageId pid = t.getRecordId().getPageId();
        HeapPage p = (HeapPage) Database.getBufferPool().getPage(tid, pid, Permissions.READ_WRITE);
        p.deleteTuple(t);
        return new ArrayList<>(Collections.singletonList(p));
    }


    // see DbFile.java for javadocs
    public DbFileIterator iterator(TransactionId tid) {
        // some code goes here
        return new DbFileIterator() {
            private int curPgNo = 0;
            private Iterator<Tuple> tupleIterator;

            @Override
            public void open() throws DbException, TransactionAbortedException {
                curPgNo = 0;
                PageId pid = new HeapPageId(getId(), curPgNo);
                HeapPage curPg = (HeapPage) Database.getBufferPool().getPage(tid, pid, Permissions.READ_ONLY);
                tupleIterator = curPg.iterator();
            }

            @Override
            public boolean hasNext() throws DbException, TransactionAbortedException {
                if (tupleIterator == null)
                    return false;
                while (!tupleIterator.hasNext() && curPgNo + 1 < numPages()){
                    ++curPgNo;
                    PageId pid = new HeapPageId(getId(), curPgNo);
                    HeapPage nxtPg = (HeapPage) Database.getBufferPool().getPage(tid, pid, Permissions.READ_ONLY);
                    tupleIterator = nxtPg.iterator();
                }
                //new page may have no nxt
                return tupleIterator.hasNext();
            }

            @Override
            public Tuple next() throws DbException, TransactionAbortedException, NoSuchElementException {
                if (tupleIterator == null)
                    throw new NoSuchElementException();
                if (tupleIterator.hasNext())
                    return tupleIterator.next();
                else return null;
                /*
                ++curPgNo;
                PageId pid = new HeapPageId(getId(), curPgNo);
                HeapPage curPg = (HeapPage) Database.getBufferPool().getPage(tid, pid, Permissions.READ_WRITE);
                tupleIterator = curPg.iterator();
                return tupleIterator.next();
                */
            }

            @Override
            public void rewind() throws DbException, TransactionAbortedException {
                close();
                open();
            }

            @Override
            public void close() {
                tupleIterator = null;
                curPgNo = 0;
            }
        };
    }

}

