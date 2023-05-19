package simpledb.optimizer;

import simpledb.common.Database;
import simpledb.common.DbException;
import simpledb.common.Type;
import simpledb.execution.Predicate;
import simpledb.execution.SeqScan;
import simpledb.storage.*;
import simpledb.transaction.Transaction;
import simpledb.transaction.TransactionAbortedException;
import simpledb.transaction.TransactionId;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * TableStats represents statistics (e.g., histograms) about base tables in a
 * query.
 * <p>
 * This class is not needed in implementing lab1 and lab2.
 */
public class TableStats {

    private static final ConcurrentMap<String, TableStats> statsMap = new ConcurrentHashMap<>();

    static final int IOCOSTPERPAGE = 1000;

    public static TableStats getTableStats(String tablename) {
        return statsMap.get(tablename);
    }

    public static void setTableStats(String tablename, TableStats stats) {
        statsMap.put(tablename, stats);
    }

    public static void setStatsMap(Map<String, TableStats> s) {
        try {
            java.lang.reflect.Field statsMapF = TableStats.class.getDeclaredField("statsMap");
            statsMapF.setAccessible(true);
            statsMapF.set(null, s);
        } catch (NoSuchFieldException | IllegalAccessException | IllegalArgumentException | SecurityException e) {
            e.printStackTrace();
        }

    }

    public static Map<String, TableStats> getStatsMap() {
        return statsMap;
    }

    public static void computeStatistics() {
        Iterator<Integer> tableIt = Database.getCatalog().tableIdIterator();

        System.out.println("Computing table stats.");
        while (tableIt.hasNext()) {
            int tableid = tableIt.next();
            TableStats s = new TableStats(tableid, IOCOSTPERPAGE);
            setTableStats(Database.getCatalog().getTableName(tableid), s);
        }
        System.out.println("Done.");
    }

    /**
     * Number of bins for the histogram. Feel free to increase this value over
     * 100, though our tests assume that you have at least 100 bins in your
     * histograms.
     */
    static final int NUM_HIST_BINS = 100;

    /**
     * Create a new TableStats object, that keeps track of statistics on each
     * column of a table
     *
     * @param tableid       The table over which to compute statistics
     * @param ioCostPerPage The cost per page of IO. This doesn't differentiate between
     *                      sequential-scan IO and disk seeks.
     */

     private DbFile db;
     private int numFields;
     private int numTuples;
     private TransactionId tid;
     private HashMap<String, IntHistogram> ihist;
     private HashMap<String, StringHistogram> shist;
     private int[] min;
     private int[] max;
     private int costperpage;
    public TableStats(int tableid, int ioCostPerPage) {
        // For this function, you'll have to get the
        // DbFile for the table in question,
        // then scan through its tuples and calculate
        // the values that you need.
        // You should try to do this reasonably efficiently, but you don't
        // necessarily have to (for example) do everything
        // in a single scan of the table.
        // TODO: some code goes here
        this.db=Database.getCatalog().getDatabaseFile(tableid);
        this.numFields =db.getTupleDesc().numFields();
        this.tid=new TransactionId();
        DbFileIterator it=db.iterator(tid);
        this.numTuples=0;
        Field f;
        this.min=new int[numFields];
        this.max=new int[numFields];
        this.costperpage=ioCostPerPage;
        String fn;

        //determine min and max values for each histogram
        try {
            it.open();
            Tuple tp=it.next();
            for(int j=0; j<numFields; j++) {
                if(tp.getField(j).getType()== Type.INT_TYPE) {
                    this.min[j]=((IntField) tp.getField(j)).getValue();
                    this.max[j]=((IntField) tp.getField(j)).getValue();
                }
            }
            while(it.hasNext()) {
                tp=it.next();
                for(int j=0; j<numFields; j++) {
                    if(tp.getField(j).getType()==Type.INT_TYPE) {
                        if(((IntField)tp.getField(j)).getValue()>max[j])
                            max[j]=((IntField) tp.getField(j)).getValue();
                        if(((IntField)tp.getField(j)).getValue()<min[j])
                            min[j]=((IntField) tp.getField(j)).getValue();
                    }
                }
            }
            it.close();
        } catch (DbException a) {} catch (TransactionAbortedException b) {}


        this.ihist=new HashMap<>();
        this.shist=new HashMap<>();
        IntHistogram i2;
        StringHistogram s2;

        try {
            it.open();
            Tuple tp=it.next();
            numTuples++;
            for(int j=0; j<this.numFields; j++) {
                f= tp.getField(j);
                fn=db.getTupleDesc().getFieldName(j);
                if(f.getType()== Type.INT_TYPE) {
                    i2=new IntHistogram(NUM_HIST_BINS, min[j], max[j]);
                    i2.addValue(((IntField) f).getValue());
                    ihist.put(fn, i2);
                }
                else {
                    s2=new StringHistogram(NUM_HIST_BINS);
                    s2.addValue(((StringField) f).getValue());
                    shist.put(fn, s2);
                }
            }

            while(it.hasNext()) {
                tp=it.next();
                numTuples++;
                for(int j=0; j<this.numFields; j++) {
                    f= tp.getField(j);
                    fn=db.getTupleDesc().getFieldName(j);
                    if(f.getType()== Type.INT_TYPE) {
                        i2=this.ihist.get(fn);
                        i2.addValue(((IntField) f).getValue());
                        if(((IntField) f).getValue()<i2.min)
                            i2.min=((IntField) f).getValue();
                        if(((IntField) f).getValue()>i2.max)
                            i2.max=((IntField) f).getValue();
                        ihist.put(fn, i2);
                    }
                    else {
                        s2=this.shist.get(fn);
                        s2.addValue(((StringField) f).getValue());
                        shist.put(fn, s2);
                    }
                }
            }
        } catch (DbException c) {} catch (TransactionAbortedException d) {}

    }

    /**
     * Estimates the cost of sequentially scanning the file, given that the cost
     * to read a page is costPerPageIO. You can assume that there are no seeks
     * and that no pages are in the buffer pool.
     * <p>
     * Also, assume that your hard drive can only read entire pages at once, so
     * if the last page of the table only has one tuple on it, it's just as
     * expensive to read as a full page. (Most real hard drives can't
     * efficiently address regions smaller than a page at a time.)
     *
     * @return The estimated cost of scanning the table.
     */
    public double estimateScanCost() {
        // TODO: some code goes here
        int pageCount =((HeapFile) db).numPages();
        return pageCount * this.costperpage;
    }

    /**
     * This method returns the number of tuples in the relation, given that a
     * predicate with selectivity selectivityFactor is applied.
     *
     * @param selectivityFactor The selectivity of any predicates over the table
     * @return The estimated cardinality of the scan with the specified
     *         selectivityFactor
     */
    public int estimateTableCardinality(double selectivityFactor) {
        // TODO: some code goes here
        return (int) (this.numTuples * selectivityFactor);
    }

    /**
     * The average selectivity of the field under op.
     *
     * @param field the index of the field
     * @param op    the operator in the predicate
     *              The semantic of the method is that, given the table, and then given a
     *              tuple, of which we do not know the value of the field, return the
     *              expected selectivity. You may estimate this value from the histograms.
     */
    public double avgSelectivity(int field, Predicate.Op op) {
        // TODO: some code goes here
        return 1.0;
    }

    /**
     * Estimate the selectivity of predicate <tt>field op constant</tt> on the
     * table.
     *
     * @param field    The field over which the predicate ranges
     * @param op       The logical operation in the predicate
     * @param constant The value against which the field is compared
     * @return The estimated selectivity (fraction of tuples that satisfy) the
     *         predicate
     */
    public double estimateSelectivity(int field, Predicate.Op op, Field constant) {
        // TODO: some code goes here
        String fn=db.getTupleDesc().getFieldName(field);
        if(constant.getType()==Type.INT_TYPE) {
            IntHistogram hist = ihist.get(fn);
            return hist.estimateSelectivity(op, ((IntField) constant).getValue());
        }
        else {
            StringHistogram hist= shist.get(fn);
            return hist.estimateSelectivity(op, ((StringField) constant).getValue());
        }
    }

    /**
     * return the total number of tuples in this table
     */
    public int totalTuples() {
        // TODO: some code goes here
        return this.numTuples;
    }

}
