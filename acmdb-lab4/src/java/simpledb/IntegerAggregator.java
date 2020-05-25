package simpledb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Knows how to compute some aggregate over a set of IntFields.
 */
public class IntegerAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;

    private int gbfield;
    private Type gbfieldtype;
    private int afield;
    private Op what;
    private TupleDesc td;

    /**
     * Aggregate constructor
     * 
     * @param gbfield
     *            the 0-based index of the group-by field in the tuple, or
     *            NO_GROUPING if there is no grouping
     * @param gbfieldtype
     *            the type of the group by field (e.g., Type.INT_TYPE), or null
     *            if there is no grouping
     * @param afield
     *            the 0-based index of the aggregate field in the tuple
     * @param what
     *            the aggregation operator
     */

    public IntegerAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        // some code goes here
        this.gbfield = gbfield;
        this.gbfieldtype = gbfieldtype;
        this.afield = afield;
        this.what = what;
        if (gbfield == Aggregator.NO_GROUPING)
            this.td = new TupleDesc(new Type[] {Type.INT_TYPE}, new String[] {"aggregateValue"});
        else
            this.td = new TupleDesc(new Type[] {gbfieldtype, Type.INT_TYPE}, new String[] {"groupValue", "aggregateValue"});
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the
     * constructor
     * 
     * @param tup
     *            the Tuple containing an aggregate field and a group-by field
     */
    private Map<Field, Integer> aMap = new HashMap<>();
    private Map<Field, Integer> cntMap = new HashMap<>();

    public void mergeTupleIntoGroup(Tuple tup) {
        // some code goes here
        Field gbField;
        if (gbfield == Aggregator.NO_GROUPING)
            gbField = null;
        else gbField = tup.getField(gbfield);

        IntField aField = (IntField) tup.getField(afield);
        Integer tupValue = aField.getValue();

        Integer oldCnt = cntMap.getOrDefault(gbField, 0);
        Integer oldVal = aMap.get(gbField);
        Integer newVal = null;
        switch (what){
            case MIN:
                if (oldVal == null)
                    newVal = tupValue;
                else newVal = Integer.min(oldVal, tupValue);
                break;
            case MAX:
                if (oldVal == null)
                    newVal = tupValue;
                else newVal = Integer.max(oldVal, tupValue);
                break;
            case SUM: case AVG:
                if (oldVal == null)
                    newVal = tupValue;
                else newVal = oldVal + tupValue;
                break;
            case COUNT:
                newVal = oldCnt + 1;
                break;
        }
        aMap.put(gbField, newVal);
        cntMap.put(gbField, oldCnt + 1);
    }

    /**
     * Create a DbIterator over group aggregate results.
     * 
     * @return a DbIterator whose tuples are the pair (groupVal, aggregateVal)
     *         if using group, or a single (aggregateVal) if no grouping. The
     *         aggregateVal is determined by the type of aggregate specified in
     *         the constructor.
     */
    public DbIterator iterator() {
        // some code goes here
        ArrayList<Tuple> tupList = new ArrayList<>();
        for (Map.Entry<Field, Integer> e : aMap.entrySet()){
            Field gbField = e.getKey();
            Integer val = e.getValue();
            if (what == Op.AVG)
                val /= cntMap.get(gbField);

            Tuple tup = new Tuple(td);
            if (gbfield == Aggregator.NO_GROUPING)
                tup.setField(0, new IntField(val));
            else {
                tup.setField(0, gbField);
                tup.setField(1, new IntField(val));
            }
            tupList.add(tup);
        }
        return new TupleIterator(td, tupList);
    }

}
