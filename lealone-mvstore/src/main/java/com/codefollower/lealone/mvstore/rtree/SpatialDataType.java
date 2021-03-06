/*
 * Copyright 2004-2013 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package com.codefollower.lealone.mvstore.rtree;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import com.codefollower.lealone.mvstore.type.DataType;
import com.codefollower.lealone.util.DataUtils;

/**
 * A spatial data type. This class supports up to 255 dimensions. Each dimension
 * can have a minimum and a maximum value of type float. For each dimension, the
 * maximum value is only stored when it is not the same as the minimum.
 */
public class SpatialDataType implements DataType {

    private final int dimensions;

    public SpatialDataType(int dimensions) {
        DataUtils.checkArgument(
                dimensions >= 1 && dimensions < 256,
                "Dimensions must be between 1 and 255, is {0}", dimensions);
        this.dimensions = dimensions;
    }

    @Override
    public int compare(Object a, Object b) {
        long la = ((SpatialKey) a).getId();
        long lb = ((SpatialKey) b).getId();
        return la < lb ? -1 : la > lb ? 1 : 0;
    }

    /**
     * Check whether two spatial values are equal.
     *
     * @param a the first value
     * @param b the second value
     * @return true if they are equal
     */
    public boolean equals(Object a, Object b) {
        long la = ((SpatialKey) a).getId();
        long lb = ((SpatialKey) b).getId();
        return la == lb;
    }

    @Override
    public int getMemory(Object obj) {
        return 40 + dimensions * 4;
    }

    @Override
    public ByteBuffer write(ByteBuffer buff, Object obj) {
        SpatialKey k = (SpatialKey) obj;
        int flags = 0;
        for (int i = 0; i < dimensions; i++) {
            if (k.min(i) == k.max(i)) {
                flags |= 1 << i;
            }
        }
        DataUtils.writeVarInt(buff, flags);
        for (int i = 0; i < dimensions; i++) {
            buff.putFloat(k.min(i));
            if ((flags & (1 << i)) == 0) {
                buff.putFloat(k.max(i));
            }
        }
        DataUtils.writeVarLong(buff, k.getId());
        return buff;
    }

    @Override
    public Object read(ByteBuffer buff) {
        int flags = DataUtils.readVarInt(buff);
        float[] minMax = new float[dimensions * 2];
        for (int i = 0; i < dimensions; i++) {
            float min = buff.getFloat();
            float max;
            if ((flags & (1 << i)) != 0) {
                max = min;
            } else {
                max = buff.getFloat();
            }
            minMax[i + i] = min;
            minMax[i + i + 1] = max;
        }
        long id = DataUtils.readVarLong(buff);
        return new SpatialKey(id, minMax);
    }

    /**
     * Check whether the two objects overlap.
     *
     * @param objA the first object
     * @param objB the second object
     * @return true if they overlap
     */
    public boolean isOverlap(Object objA, Object objB) {
        SpatialKey a = (SpatialKey) objA;
        SpatialKey b = (SpatialKey) objB;
        for (int i = 0; i < dimensions; i++) {
            if (a.max(i) < b.min(i) || a.min(i) > b.max(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Increase the bounds in the given spatial object.
     *
     * @param bounds the bounds (may be modified)
     * @param add the value
     */
    public void increaseBounds(Object bounds, Object add) {
        SpatialKey b = (SpatialKey) bounds;
        SpatialKey a = (SpatialKey) add;
        for (int i = 0; i < dimensions; i++) {
            b.setMin(i, Math.min(b.min(i), a.min(i)));
            b.setMax(i, Math.max(b.max(i), a.max(i)));
        }
    }

    /**
     * Get the area increase by extending a to contain b.
     *
     * @param objA the bounding box
     * @param objB the object
     * @return the area
     */
    public float getAreaIncrease(Object objA, Object objB) {
        SpatialKey a = (SpatialKey) objA;
        SpatialKey b = (SpatialKey) objB;
        float min = a.min(0);
        float max = a.max(0);
        float areaOld = max - min;
        min = Math.min(min,  b.min(0));
        max = Math.max(max,  b.max(0));
        float areaNew = max - min;
        for (int i = 1; i < dimensions; i++) {
            min = a.min(i);
            max = a.max(i);
            areaOld *= max - min;
            min = Math.min(min,  b.min(i));
            max = Math.max(max,  b.max(i));
            areaNew *= max - min;
        }
        return areaNew - areaOld;
    }

    /**
     * Get the combined area of both objects.
     *
     * @param objA the first object
     * @param objB the second object
     * @return the area
     */
    float getCombinedArea(Object objA, Object objB) {
        SpatialKey a = (SpatialKey) objA;
        SpatialKey b = (SpatialKey) objB;
        float area = 1;
        for (int i = 0; i < dimensions; i++) {
            float min = Math.min(a.min(i),  b.min(i));
            float max = Math.max(a.max(i),  b.max(i));
            area *= max - min;
        }
        return area;
    }

    /**
     * Check whether a contains b.
     *
     * @param objA the bounding box
     * @param objB the object
     * @return the area
     */
    public boolean contains(Object objA, Object objB) {
        SpatialKey a = (SpatialKey) objA;
        SpatialKey b = (SpatialKey) objB;
        for (int i = 0; i < dimensions; i++) {
            if (a.min(i) > b.min(i) || a.max(i) < b.max(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check whether a is completely inside b and does not touch the
     * given bound.
     *
     * @param objA the object to check
     * @param objB the bounds
     * @return true if a is completely inside b
     */
    public boolean isInside(Object objA, Object objB) {
        SpatialKey a = (SpatialKey) objA;
        SpatialKey b = (SpatialKey) objB;
        for (int i = 0; i < dimensions; i++) {
            if (a.min(i) <= b.min(i) || a.max(i) >= b.max(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Create a bounding box starting with the given object.
     *
     * @param objA the object
     * @return the bounding box
     */
    Object createBoundingBox(Object objA) {
        float[] minMax = new float[dimensions * 2];
        SpatialKey a = (SpatialKey) objA;
        for (int i = 0; i < dimensions; i++) {
            minMax[i + i] = a.min(i);
            minMax[i + i + 1] = a.max(i);
        }
        return new SpatialKey(0, minMax);
    }

    /**
     * Get the most extreme pair (elements that are as far apart as possible).
     * This method is used to split a page (linear split). If no extreme objects
     * could be found, this method returns null.
     *
     * @param list the objects
     * @return the indexes of the extremes
     */
    public int[] getExtremes(ArrayList<Object> list) {
        SpatialKey bounds = (SpatialKey) createBoundingBox(list.get(0));
        SpatialKey boundsInner = (SpatialKey) createBoundingBox(bounds);
        for (int i = 0; i < dimensions; i++) {
            float t = boundsInner.min(i);
            boundsInner.setMin(i, boundsInner.max(i));
            boundsInner.setMax(i, t);
        }
        for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);
            increaseBounds(bounds, o);
            increaseMaxInnerBounds(boundsInner, o);
        }
        double best = 0;
        int bestDim = 0;
        for (int i = 0; i < dimensions; i++) {
            float inner = boundsInner.max(i) - boundsInner.min(i);
            if (inner < 0) {
                continue;
            }
            float outer = bounds.max(i) - bounds.min(i);
            float d = inner / outer;
            if (d > best) {
                best = d;
                bestDim = i;
            }
        }
        if (best <= 0) {
            return null;
        }
        float min = boundsInner.min(bestDim);
        float max = boundsInner.max(bestDim);
        int firstIndex = -1, lastIndex = -1;
        for (int i = 0; i < list.size() && (firstIndex < 0 || lastIndex < 0); i++) {
            SpatialKey o = (SpatialKey) list.get(i);
            if (firstIndex < 0 && o.max(bestDim) == min) {
                firstIndex = i;
            } else if (lastIndex < 0 && o.min(bestDim) == max) {
                lastIndex = i;
            }
        }
        return new int[] { firstIndex, lastIndex };
    }

    private void increaseMaxInnerBounds(Object bounds, Object add) {
        SpatialKey b = (SpatialKey) bounds;
        SpatialKey a = (SpatialKey) add;
        for (int i = 0; i < dimensions; i++) {
            b.setMin(i, Math.min(b.min(i), a.max(i)));
            b.setMax(i, Math.max(b.max(i), a.min(i)));
        }
    }

}
