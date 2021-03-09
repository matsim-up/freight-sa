package org.matsim.up.freight.clustering;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class ClusterUtilsTest {

    @Test
    public void getLongitudinalMonths() {
        List<String> dates = ClusterUtils.getLongitudinalMonths();
        Assert.assertEquals("Wrong number of entries.", 53, dates.size());
        Assert.assertEquals("Wrong first entry.", "201001", dates.get(0));
        Assert.assertEquals("Wrong last entry.", "201405", dates.get(dates.size()-1));
    }

}