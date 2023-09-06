package com.mycompany.imagej;

public class LinearRegressionModel extends RegressionModel {

    // The y intercept of the straight line
    private double a;

    // The gradient of the line
    private double b;

    /**
     * Construct a new LinearRegressionModel with the supplied data set
     *
     * @param x
     * The x data points
     * @param y
     * The y data points
     */
    public LinearRegressionModel(float[] x, float[] y) {
        super(x, y);
        a = b = 0;
    }

    /**
     * Get the coefficents of the fitted straight line
     *
     * @return An array of coefficients {intercept, gradient}
     *
     * @see RegressionModel#getCoefficients()
     */
    @Override
    public double[] getCoefficients() {
        if (!computed)
            throw new IllegalStateException("Model has not yet computed");

        return new double[] { a, b};
    }

    /**
     * Compute the coefficients of a straight line the best fits the data set
     *
     * @see RegressionModel #compute()
     */
    @Override
    public void compute() {

        // throws exception if regression can not be performed
        if (xValues.length < 2 | yValues.length < 2) {
            throw new IllegalArgumentException("Must have more than two values");
        }

        // get the value of the gradient using the formula b = cov[x,y] / var[x]
        b = MathUtils.covariance(xValues, yValues) / MathUtils.variance(xValues);

        // get the value of the y-intercept using the formula a = ybar + b \* xbar
        a = MathUtils.mean(yValues) - b * MathUtils.mean(xValues);

        // set the computed flag to true after we have calculated the coefficients
        computed = true;
    }

    /**
     * Evaluate the computed model at a certain point
     *
     * @param x The point to evaluate at
     * @return The value of the fitted straight line at the point x
     * @see RegressionModel evaluateAt(double)
     */
    @Override
    public float evaluateAt(float x) {
        if (!computed)
            throw new IllegalStateException("Model has not yet computed");
        return (float) (a + b * x);
    }

    public float getError(float x, float y){
        if (!computed) {
            throw new IllegalStateException("Model has not yet computed");
        }
        return (float) (y - a - (b * x));
    }

    public static class MathUtils {
        public static double covariance(float[] x, float[] y) {
            double xmean = mean(x);
            double ymean = mean(y);

            double result = 0;

            for (int i = 0; i < x.length; i++) {
                result += (x[i] - xmean) * (y[i] - ymean);
            }

            result /= (x.length);
            return result;
        }

        /**
         * Calculate the mean of a data set
         *
         * @param data
         * The data set to calculate the mean of
         * @return The mean of the data set
         */
        public static float mean(float[] data) {
            float sum = 0;

            for (int i = 0; i < data.length; i++) {
                sum += data[i];
            }

            return sum / data.length;
        }

        /**
         * Calculate the variance of a data set
         *
         * @param data
         * The data set to calculate the variance of
         * @return The variance of the data set
         */
        public static float variance(float[] data) {
            // Get the mean of the data set
            float mean = mean(data);

            float sumOfSquaredDeviations = 0;

            // Loop through the data set
            for (int i = 0; i < data.length; i++) {
                // sum the difference between the data element and the mean squared
                sumOfSquaredDeviations += Math.pow(data[i] - mean, 2);
            }

            // Divide the sum by the length of the data set - 1 to get our result
            return sumOfSquaredDeviations / (data.length);
        }

    }

}