package com.ning.compress.lzf;

import java.io.IOException;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestLZFEncoder
{
    @Test
    public void testSizeEstimate() throws IOException
    {
        int max = LZFEncoder.estimateMaxWorkspaceSize(10000);
        // somewhere between 103 and 105%
        if (max < 10300 || max > 10500) {
            Assert.fail("Expected ratio to be 1010 <= x <= 1050, was: "+max);
        }
    }
}
