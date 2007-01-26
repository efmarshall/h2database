/*
 * Copyright 2004-2006 H2 Group. Licensed under the H2 License, Version 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.synth;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class TestHaltApp extends TestHalt {
    
    private int rowCount;
    
    public static void main(String[] args) throws Exception {
        new TestHaltApp().start(args);
    }

    protected void testInit() throws SQLException {
        Statement stat = conn.createStatement();
        try {
            stat.execute("DROP TABLE TEST");
        } catch(SQLException e) {
            // ignore
        }
        // stat.execute("CREATE TABLE TEST(ID IDENTITY, NAME VARCHAR(255))");
        for(int i=0; i< 20; i++) {
            stat.execute("DROP TABLE IF EXISTS TEST" + i);
            stat.execute("CREATE TABLE TEST"+i +"(ID INT PRIMARY KEY, NAME VARCHAR(255))");
        }
        for(int i=0; i< 20; i+=2) {
            stat.execute("DROP TABLE TEST" + i);
        }
        stat.execute("CREATE TABLE TEST(ID BIGINT GENERATED BY DEFAULT AS IDENTITY, NAME VARCHAR(255), DATA CLOB)");
    }
    
    protected void testWaitAfterAppStart() throws Exception {
        int sleep = 10 + random.nextInt(300);
        Thread.sleep(sleep);
    }
    
    protected void testCheckAfterCrash() throws Exception {
        Statement stat = conn.createStatement();
        ResultSet rs = stat.executeQuery("SELECT COUNT(*) FROM TEST");
        rs.next();
        int count = rs.getInt(1);
        System.out.println("count: " + count);
        if(count % 2 == 1) {
            throw new Exception("Unexpected odd row count");
        }
    }
    
    protected void appStart() throws SQLException {
        Statement stat = conn.createStatement();
        if((flags & FLAG_NO_DELAY) != 0) {
            stat.execute("SET WRITE_DELAY 0");
            stat.execute("SET MAX_LOG_SIZE 1");
        }
        ResultSet rs = stat.executeQuery("SELECT COUNT(*) FROM TEST");
        rs.next();
        rowCount = rs.getInt(1);
        log("rows: " + rowCount, null);
    }
    
    protected void appRun() throws SQLException {
        conn.setAutoCommit(false);
        int rows = 10000 + value;
        PreparedStatement prepInsert = conn.prepareStatement("INSERT INTO TEST(NAME, DATA) VALUES('Hello World', ?)");
        PreparedStatement prepUpdate = conn.prepareStatement("UPDATE TEST SET NAME = 'Hallo Welt', DATA = ? WHERE ID = ?");
        for(int i=0; i<rows; i++) {
            Statement stat = conn.createStatement();
            if((operations & OP_INSERT) != 0) {
                if((flags & FLAG_LOBS) != 0) {
                    prepInsert.setString(1, getRandomString(random.nextInt(200)));
                    prepInsert.execute();
                } else {
                    stat.execute("INSERT INTO TEST(NAME) VALUES('Hello World')");
                }
                rowCount++;
            }
            if((operations & OP_UPDATE) != 0) {
                if((flags & FLAG_LOBS) != 0) {
                    prepUpdate.setString(1, getRandomString(random.nextInt(200)));
                    prepUpdate.setInt(2, random.nextInt(rowCount+1));
                    prepUpdate.execute();
                } else {
                    stat.execute("UPDATE TEST SET VALUE = 'Hallo Welt' WHERE ID = " + random.nextInt(rowCount+1));
                }
            }
            if((operations & OP_DELETE) != 0) {
                int uc = stat.executeUpdate("DELETE FROM TEST WHERE ID = " + random.nextInt(rowCount+1));
                rowCount-=uc;
            }
            log("rows now: " + rowCount, null);
            if(rowCount % 2 == 0) {
                conn.commit();
                log("committed: " + rowCount, null);
            }
            if((flags & FLAG_NO_DELAY) != 0) {
                if(random.nextInt(100) == 0) {
                    stat.execute("CHECKPOINT");
                }
            }
        }
        conn.rollback();
    }

}
