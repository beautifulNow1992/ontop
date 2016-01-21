package it.unibz.krdb.obda.utils;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author xiao
 */
public class VersionInfoTest {

    @Test
    public void testGetVersion() throws Exception {
        String version = VersionInfo.getVersionInfo().getVersion();
        System.out.println(version);
        assertNotNull(version);
    }
}