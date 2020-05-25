package simpledb;

/** A class to represent a fixed-width histogram over a single integer-based field.
 */
public class IntHistogram {

    private int bucketNum, min, max, w;
    private int buckets[];
    private int nTup;

    /**
     * Create a new IntHistogram.
     * 
     * This IntHistogram should maintain a histogram of integer values that it receives.
     * It should split the histogram into "buckets" buckets.
     * 
     * The values that are being histogrammed will be provided one-at-a-time through the "addValue()" function.
     * 
     * Your implementation should use space and have execution time that are both
     * constant with respect to the number of values being histogrammed.  For example, you shouldn't 
     * simply store every value that you see in a sorted list.
     * 
     * @param buckets The number of buckets to split the input value into.
     * @param min The minimum integer value that will ever be passed to this class for histogramming
     * @param max The maximum integer value that will ever be passed to this class for histogramming
     */
    public IntHistogram(int buckets, int min, int max) {
    	// some code goes here
        this.bucketNum = buckets;
        this.min = min;
        this.max = max;
        this.w = (max - min + buckets) / buckets;
        this.buckets = new int[buckets];
        this.nTup = 0;
    }

    /**
     * Add a value to the set of values that you are keeping a histogram of.
     * @param v Value to add to the histogram
     */
    public void addValue(int v) {
    	// some code goes here
        ++nTup;
        ++buckets[(v - min) / w];
    }

    /**
     * Estimate the selectivity of a particular predicate and operand on this table.
     * 
     * For example, if "op" is "GREATER_THAN" and "v" is 5, 
     * return your estimate of the fraction of elements that are greater than 5.
     * 
     * @param op Operator
     * @param v Value
     * @return Predicted selectivity of this particular operator and value
     */
    public double estimateSelectivity(Predicate.Op op, int v) {

    	// some code goes here
        int b = (v - min) / w;
        double sel = 0.0;
        double b_part = 0.0;
        switch (op){
            case EQUALS:
                if (v < min || v > max)
                    return 0.0;
                return buckets[b] * 1.0 / w / nTup;
            case NOT_EQUALS:
                return 1 - estimateSelectivity(Predicate.Op.EQUALS, v);
            case LESS_THAN:
                if (v <= min)
                    return 0.0;
                if (v > max)
                    return 1.0;
                for (int i = 0; i < b; ++i)
                    sel += buckets[i];
                b_part = (v - (min + b * w)) * 1.0 / w;
                sel += buckets[b] * b_part;
                return sel / nTup;
            case LESS_THAN_OR_EQ:
                return estimateSelectivity(Predicate.Op.LESS_THAN, v+1);
            case GREATER_THAN:
                if (v >= max)
                    return 0.0;
                if (v < min)
                    return 1.0;
                for (int i = b + 1; i < bucketNum; ++i)
                    sel += buckets[i];
                b_part = ((min + (b + 1) * w) - 1 - v) * 1.0 / w;
                sel += buckets[b] * b_part;
                return sel / nTup;
            case GREATER_THAN_OR_EQ:
                return estimateSelectivity(Predicate.Op.GREATER_THAN, v - 1);
            default:
                break;
        }
        throw new RuntimeException("Why I enter here when estimateSelectivity");
    }
    
    /**
     * @return
     *     the average selectivity of this histogram.
     *     
     *     This is not an indispensable method to implement the basic
     *     join optimization. It may be needed if you want to
     *     implement a more efficient optimization
     * */
    public double avgSelectivity()
    {
        // some code goes here
        return 1.0;
    }
    
    /**
     * @return A string describing this histogram, for debugging purposes
     */
    public String toString() {
        // some code goes here
        return null;
    }
}
