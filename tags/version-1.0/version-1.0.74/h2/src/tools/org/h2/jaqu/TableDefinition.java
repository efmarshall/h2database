/*
 * Copyright 2004-2008 H2 Group. Multiple-Licensed under the H2 License, 
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.jaqu;

//## Java 1.6 begin ##
import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

import org.h2.jaqu.util.Utils;
//## Java 1.6 end ##

/**
 * A table definition contains the index definitions of a table, the field
 * definitions, the table name, and other meta data.
 * 
 * @param <T> the table type
 */
//## Java 1.6 begin ##
class TableDefinition<T> {
//## Java 1.6 end ##

    /**
     * The meta data of an index.
     */
//## Java 1.6 begin ##
    static class IndexDefinition {
        boolean unique;
        String indexName;
        String[] columnNames;
    }
//## Java 1.6 end ##
    
    /**
     * The meta data of a field.
     */
//## Java 1.6 begin ##
    static class FieldDefinition<X> {
        String columnName;
        Field field;
        String dataType;
        public Object getValue(Object obj) {
            try {
                return field.get(obj);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        public void initWithNewObject(Object obj) {
            Object o = Utils.newObject(field.getType());
            setValue(obj, o);
        }
        void setValue(Object obj, Object o) {
            try {
                field.set(obj, o);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        
        @SuppressWarnings("unchecked")
        public X read(ResultSet rs) {
            try {
                return (X) rs.getObject(columnName);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    String tableName;
    private Class<T> clazz;
    private ArrayList<FieldDefinition> fields = Utils.newArrayList();
    private IdentityHashMap<Object, FieldDefinition> fieldMap = 
            Utils.newIdentityHashMap();
    private String[] primaryKeyColumnNames;
    private ArrayList<IndexDefinition> indexes = Utils.newArrayList();

    TableDefinition(Class<T> clazz) {
        this.clazz = clazz;
        tableName = clazz.getSimpleName();
    }
    
    void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public void setPrimaryKey(Object[] primaryKeyColumns) {
        this.primaryKeyColumnNames = mapColumnNames(primaryKeyColumns);
    }
    
    <A> String getColumnName(A fieldObject) {
        FieldDefinition def = fieldMap.get(fieldObject);
        return def == null ? null : def.columnName;
    }
    
    private String[] mapColumnNames(Object[] columns) {
        int len = columns.length;
        String[] columnNames = new String[len];
        for (int i = 0; i < len; i++) {
            columnNames[i] = getColumnName(columns[i]);
        }
        return columnNames;
    }
    
    public void addIndex(Object[] columns) {
        IndexDefinition index = new IndexDefinition();
        index.indexName = tableName + "_" + indexes.size();
        index.columnNames = mapColumnNames(columns);
        indexes.add(index);
    }

    public void apply() {
        // TODO Auto-generated method stub
        
    }

    public void mapFields() {
        Field[] classFields = clazz.getFields();
        for (Field f : classFields) {
            FieldDefinition fieldDef = new FieldDefinition();
            fieldDef.field = f;
            fieldDef.columnName = f.getName();
            fieldDef.dataType = getDataType(f);
            fields.add(fieldDef);
        }
    }
    
    private String getDataType(Field field) {
        Class< ? > clazz = field.getType();
        if (clazz == Integer.class) {
            return "INT";
        } else if (clazz == String.class) {
            return "VARCHAR";
        } else if (clazz == Double.class) {
            return "DOUBLE";
        }
        return "VARCHAR";
        // TODO add more data types
    }
    
    void insert(Db db, Object obj) {
        StringBuilder buff = new StringBuilder("INSERT INTO ");
        buff.append(tableName);
        buff.append(" VALUES(");
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                buff.append(", ");
            }
            buff.append('?');
        }        
        buff.append(')');
        String sql = buff.toString();
        PreparedStatement prep = db.prepare(sql);
        for (int i = 0; i < fields.size(); i++) {
            FieldDefinition field = fields.get(i);
            Object value = field.getValue(obj);
            setValue(prep, i + 1, value);
        }        
        try {
            prep.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    
    private void setValue(PreparedStatement prep, int parameterIndex, Object x) {
        try {
            prep.setObject(parameterIndex, x);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public TableDefinition createTableIfRequired(Db db) {
        StringBuilder buff = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
        buff.append(tableName);
        buff.append('(');
        for (int i = 0; i < fields.size(); i++) {
            FieldDefinition field = fields.get(i);
            if (i > 0) {
                buff.append(", ");
            }
            buff.append(field.columnName);
            buff.append(' ');
            buff.append(field.dataType);
        }
        if (primaryKeyColumnNames != null) {
            buff.append(", PRIMARY KEY(");
            for (int i = 0; i < primaryKeyColumnNames.length; i++) {
                if (i > 0) {
                    buff.append(", ");
                }                
                buff.append(primaryKeyColumnNames[i]);
            }
            buff.append(')');
        }
        buff.append(')');
        db.execute(buff.toString());
        // TODO create indexes
        return this;
    }

    public void mapObject(Object obj) {
        fieldMap.clear();
        initObject(obj, fieldMap);
    }
    
    void initObject(Object obj, Map<Object, FieldDefinition> map) {
        for (FieldDefinition def : fields) {
            def.initWithNewObject(obj);
            map.put(def.getValue(obj), def);
        }
    }

    void readRow(Object item, ResultSet rs) {
        for (FieldDefinition def : fields) {
            Object o = def.read(rs);
            def.setValue(item, o);
        }
    }
    
    <U, X> void copyAttributeValues(Db db, U from, X to, X map) {
        for (FieldDefinition def : fields) {
            Object obj = def.getValue(map);
            FieldDefinition fd = db.getFieldDefinition(obj);
            Object value = fd.getValue(from);
            def.setValue(to, value);
        }
    }

}
//## Java 1.6 end ##