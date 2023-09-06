package com.mycompany.imagej;

public abstract class RegressionModel {

    // The X values of the data set points
    protected float[] xValues;

    // he X values of the data set points
    protected float[] yValues;

    // Have the unknown parameters been calculated yet?
    protected boolean computed;

    public RegressionModel(float[] x, float[] y) {
        this.xValues = x;
        this.yValues = y;
        computed = false;
    }

    public float[] getXValues() {
        return this.xValues;
    }

    public float[] getYValues() {
        return this.yValues;
    }

    public abstract double[] getCoefficients();

    public abstract void compute();

    public abstract float evaluateAt(float x);
}

