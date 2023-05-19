package simpledb.optimizer;

import simpledb.execution.Predicate;

/**
 * A class to represent a fixed-width histogram over a single integer-based field.
 */
public class IntHistogram {

    /**
     * Create a new IntHistogram.
     * <p>
     * This IntHistogram should maintain a histogram of integer values that it receives.
     * It should split the histogram into "buckets" buckets.
     * <p>
     * The values that are being histogrammed will be provided one-at-a-time through the "addValue()" function.
     * <p>
     * Your implementation should use space and have execution time that are both
     * constant with respect to the number of values being histogrammed.  For example, you shouldn't
     * simply store every value that you see in a sorted list.
     *
     * @param buckets The number of buckets to split the input value into.
     * @param min     The minimum integer value that will ever be passed to this class for histogramming
     * @param max     The maximum integer value that will ever be passed to this class for histogramming
     */

   
    public int min;
    private int[] buckets;
    public int max;
    private int sizeofBucket;
    private int numberOfTuples;
    public int numberOfBuckets;

    public IntHistogram(int buckets, int min, int max) {
        // TODO: some code goes here
      
        this.max=max;
        this.numberOfBuckets=buckets;
        this.min=min;
        this.buckets=new int[numberOfBuckets];
        this.numberOfTuples=0;
        this.sizeofBucket=(int) Math.ceil(((double) (this.max - this.min+1))/numberOfBuckets);

    }

    /**
     * Add a value to the set of values that you are keeping a histogram of.
     *
     * @param v Value to add to the histogram
     */
    public void addValue(int v) {
        // TODO: some code goes here
        int idx = (v-this.min)/this.sizeofBucket;
        buckets[idx]++;
        this.numberOfTuples++;
    }

    /**
     * Estimate the selectivity of a particular predicate and operand on this table.
     * <p>
     * For example, if "op" is "GREATER_THAN" and "v" is 5,
     * return your estimate of the fraction of elements that are greater than 5.
     *
     * @param op Operator
     * @param v  Value
     * @return Predicted selectivity of this particular operator and value
     */
    public double estimateSelectivity(Predicate.Op op, int v) {
        // TODO: some code goes here
        if(v>this.max) {
            if(op==Predicate.Op.EQUALS || op==Predicate.Op.GREATER_THAN) {
                return 0;
            }
            if(op==Predicate.Op.GREATER_THAN_OR_EQ || op==Predicate.Op.LIKE) {
                return 0;
            }
            return 1;
        }

        if(v<this.min) {
            if(op==Predicate.Op.EQUALS || op==Predicate.Op.LESS_THAN)
                return 0;
            if(op==Predicate.Op.LESS_THAN_OR_EQ || op== Predicate.Op.LIKE)
                return 0;
            return 1;
        }

        int idx=(v-this.min)/this.sizeofBucket;
        double w_ =this.sizeofBucket;
        double h_ =buckets[idx];

    	if (op==Predicate.Op.EQUALS) {
            return h_/w_/this.numberOfTuples;
        }
        else if (op==Predicate.Op.NOT_EQUALS) {
            return 1-h_/w_/numberOfTuples;
        }

        
        int rightBuck= ((int) w_)*(idx+1)+this.min-1;
        double b__=(rightBuck-v)/w_;
        double bucket_prev=h_/numberOfTuples;
        double rrnext=b__*bucket_prev;
        double resultFrmOtherBuckets=0;

        int k = idx+1;
        while (k < numberOfBuckets){
            resultFrmOtherBuckets=resultFrmOtherBuckets+buckets[k];
            k++;
        }

        resultFrmOtherBuckets=resultFrmOtherBuckets/numberOfTuples;
        double gsec=resultFrmOtherBuckets+rrnext;

        if(op== Predicate.Op.GREATER_THAN)
            return gsec;
        else if (op== Predicate.Op.GREATER_THAN_OR_EQ) {
            double res = gsec+h_/w_/this.numberOfTuples;
            return res;
        }
        else if (op==Predicate.Op.LESS_THAN_OR_EQ) {
            return 1-gsec;
        }
        else if (op==Predicate.Op.LESS_THAN) {
            double res = 1-gsec-h_/w_/this.numberOfTuples;
            return res;
        }
        else if (op== Predicate.Op.LIKE) {
            double res =  1/numberOfBuckets;
            return res;
        }
        return -1.0;
    }

    /**
     * @return the average selectivity of this histogram.
     *         <p>
     *         This is not an indispensable method to implement the basic
     *         join optimization. It may be needed if you want to
     *         implement a more efficient optimization
     */
    public double avgSelectivity() {
        // TODO: some code goes here
        return 1.0;
    }

    /**
     * @return A string describing this histogram, for debugging purposes
     */
    public String toString() {
        // TODO: some code goes here
        String s = "";
        int idx = 0;
        while (idx < numberOfBuckets){
            s = s + Integer.toString(idx) + " = " + Integer.toString(buckets[idx]) + "; ";
            idx++;
        }
        return s; 
    }
}
