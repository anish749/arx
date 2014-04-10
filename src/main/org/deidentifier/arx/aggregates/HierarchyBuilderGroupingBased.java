/*
 * ARX: Efficient, Stable and Optimal Data Anonymization
 * Copyright (C) 2012 - 2014 Florian Kohlmayer, Fabian Prasser
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.deidentifier.arx.aggregates;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.deidentifier.arx.AttributeType.Hierarchy;
import org.deidentifier.arx.DataType;

/**
 * This abstract base class enables building hierarchies for categorical and non-categorical values
 * 
 * @author Fabian Prasser
 *
 * @param <T>
 */
public abstract class HierarchyBuilderGroupingBased<T> extends HierarchyBuilder<T> implements Serializable {

    /**
     * This class represents a fanout parameter
     * @author Fabian Prasser
     */
    public static class Group<U> implements Serializable {
        
        private static final long serialVersionUID = -5767501048737045793L;
        
        /** Fanout*/
        private final int size;
        /** Aggregate function*/
        private final AggregateFunction<U> function;
        
        /**
         * Creates a new instance
         * @param fanout
         * @param function
         */
        private Group(int fanout, AggregateFunction<U> function) {
            if (fanout<=0) {
                throw new IllegalArgumentException("Size must be >= 0");
            }
            if (function==null) {
                throw new IllegalArgumentException("Function must not be null");
            }
            this.size = fanout;
            this.function = function;
        }

        /**
         * @return the size
         */
        public int getSize() {
            return size;
        }

        /**
         * @return the function
         */
        public AggregateFunction<U> getFunction() {
            return function;
        }
        
        @Override
        public String toString(){
            return "Group[length="+size+", function="+function.toString()+"]";
        }
    }
    
    /**
     * This class represents a level in the hierarchy
     * @author Fabian Prasser
     */
    public static class Level<U> implements Serializable{
        
        private static final long serialVersionUID = 1410005675926162598L;
        /** Level*/
        private final int level;
        /** List of groups*/
        private final List<Group<U>> list = new ArrayList<Group<U>>();
        /** Builder*/
        private final HierarchyBuilderGroupingBased<U> builder;
        
        /**
         * Creates a new instance
         * @param level
         */
        private Level(HierarchyBuilderGroupingBased<U> builder, int level) {
            this.level = level;
            this.builder = builder;
        }
        
        /**
         * Adds the given group with the default aggregate function
         * @param fanout
         * @return
         */
        public Level<U> addGroup(int fanout) {
            if (builder.getDefaultFunction() == null) {
                throw new IllegalStateException("No default aggregate function defined");
            }
            this.list.add(new Group<U>(fanout, builder.getDefaultFunction()));
            builder.setPrepared(false);
            return this;
        }

        /**
         * Adds the given group with the given aggregate function
         * @param fanout
         * @param function
         * @return
         */
        public Level<U> addGroup(int fanout, AggregateFunction<U> function) {
            this.list.add(new Group<U>(fanout, function));
            builder.setPrepared(false);
            return this;
        }

        /**
         * Adds the given group. The result will be labeled with the given string
         * @param fanout
         * @param label
         * @return
         */
        public Level<U> addGroup(int fanout, String label) {
            this.list.add(new Group<U>(fanout, AggregateFunction.forType(builder.getDataType()).createConstantFunction(label)));
            builder.setPrepared(false);
            return this;
        }

        /**
         * Removes all groups on this level
         * @return
         */
        public Level<U> clearGroups() {
            this.list.clear();
            builder.setPrepared(false);
            return this;
        }
        
        /**
         * Returns the list
         * @return
         */
        @SuppressWarnings("unchecked")
        public List<Group<U>> getGroups(){
            return (List<Group<U>>)((ArrayList<Group<U>>)this.list).clone();
        }

        /**
         * @return the level
         */
        public int getLevel() {
            return level;
        }
     
        @Override
        public String toString(){
            StringBuilder b = new StringBuilder();
            b.append("Level[height="+level+"]\n");
            for (int i=0, length=list.size(); i<length; i++){
                Group<U> fanout = list.get(i);
                b.append("   ").append(fanout.toString());
                if (i<length-1) b.append("\n");
            }
            return b.toString();
        }
    }
    
    /**
     * A group representation to be used by subclasses
     * @author Fabian Prasser
     */
    protected abstract static class AbstractGroup implements Serializable {
        
        private static final long serialVersionUID = -7657969446040078411L;
        
        private String label;
        
        protected AbstractGroup(String label){
            this.label = label;
        }
        protected String getLabel(){
            return label;
        }
    }
    
    private static final long serialVersionUID = 3208791665131141362L;
    /** The data array*/
    private transient String[] data;
    /** All fanouts for each level */
    private Map<Integer, Level<T>> groups = new HashMap<Integer, Level<T>>();
    /** The groups on the first level*/
    private transient AbstractGroup[][] abstractGroups;
    /** Are we ready to go*/
    private transient boolean prepared = false;
    /** The data type*/
    private DataType<T> datatype;
    /** The default aggregate function, might be null*/
    private AggregateFunction<T> function;

    /**
     * Creates a new instance for the given data type
     * @param type
     */
    protected HierarchyBuilderGroupingBased(Type type, DataType<T> datatype){
        super(type);
        this.datatype = datatype;
    }
    
    /**
     * Creates a new hierarchy, based on the predefined specification
     * @return
     */
    public Hierarchy create(){
        
        if (!prepared) {
            throw new IllegalStateException("Please call prepare() first");
        }

        // Add input data
        String[][] result = new String[data.length][abstractGroups.length + 1];
        for (int i=0; i<result.length; i++) {
            result[i] = new String[abstractGroups.length + 1];
            result[i][0] = data[i];
        }
        
        // Add levels
        for (int i=0; i<result[0].length - 1; i++){
            Map<String, Map<AbstractGroup, String>> multiplicities = new HashMap<String, Map<AbstractGroup, String>>();
            for (int j=0; j<result.length; j++){
                result[j][i + 1] = getLabel(multiplicities, abstractGroups[i][j]);
            }
        }
        
        
        Hierarchy h = Hierarchy.create(result);
        
        this.prepared = false;
        this.data = null;
        this.abstractGroups = null;
        return h;
    }
    
    /**
     * Returns the given level
     * @param level
     * @return 
     */
    public Level<T> getLevel(int level){
        if (!this.groups.containsKey(level)) {
            this.groups.put(level, new Level<T>(this, level));
            this.setPrepared(false);
        }
        return this.groups.get(level);
    }

    /**
     * Returns all currently defined levels
     * @return
     */
    public List<Level<T>> getLevels(){
        List<Level<T>> levels = new ArrayList<Level<T>>();
        levels.addAll(this.groups.values());
        Collections.sort(levels, new Comparator<Level<T>>(){
            @Override
            public int compare(Level<T> o1,
                               Level<T> o2) {
                return new Integer(o1.getLevel()).compareTo(new Integer(o2.getLevel()));
            }
        });
        return levels;
    }

    /**
     * Returns whether the current configuration is valid. Returns <code>null</code>, if so, an error message
     * if not.
     * @return
     */
    public String isValid() {
        
        // Check fanouts
        int max = 0;
        for (Entry<Integer, Level<T>> level : this.groups.entrySet()) {
            if (level.getValue().getGroups().isEmpty()) {
                if (level.getKey() < this.groups.size()-1) {
                    return "No group specified on level "+level.getKey();
                }
            }
            max = Math.max(level.getKey(), max);
        }
        for (int i=0; i<max; i++){
            if (!this.groups.containsKey(i)) {
                return "Missing specification for level "+i;
            } else if (this.groups.get(i).getGroups().isEmpty()) {
                return "Missing specification for level "+i;
            }
        }
        
        return null;
    }
    
    /**
     * Prepares the builder. Returns a list of the number of equivalence classes per level
     * @return
     */
    public int[] prepare(String[] data){
        this.data = data;
        String error = this.isValid();
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        this.abstractGroups = prepareGroups();
        this.prepared = true;
        
       // TODO: This assumes that input data does not contain duplicates
        int[] result = new int[this.abstractGroups.length + 1];
        result[0] = data.length; 
        for (int i=0; i<result.length - 1; i++){
            Set<AbstractGroup> set = new HashSet<AbstractGroup>();
            for (int j=0; j<this.abstractGroups[i].length; j++){
                set.add(abstractGroups[i][j]);
            }
            result[i + 1] = set.size();
        }
        return result;
    }
    
    /**
     * Sets the default aggregate function to be used by all fanouts
     * @param function
     */
    public void setAggregateFunction(AggregateFunction<T> function){
        if (function == null) {
            throw new IllegalArgumentException("Function must not be null");
        }
        this.function = function;
    }
    
    /**
     * Returns the label for a given group. Makes sure that no labels are returned twice
     * @param multiplicities
     * @param group
     * @return
     */
    private String getLabel(Map<String, Map<AbstractGroup, String>> multiplicities, AbstractGroup group) {
        String label = group.getLabel();
        Map<AbstractGroup, String> map = multiplicities.get(label);
        if (map == null) {
            map = new HashMap<AbstractGroup, String>();
            map.put(group, label);
            multiplicities.put(label, map);
            return label;
        } else {
            String storedLabel = map.get(group);
            if (storedLabel != null) {
                return storedLabel;
            } else {
                label +="-"+map.size();
                map.put(group, label);
                return label;
            }
        }
    }
    
    /**
     * Returns the data array
     * @return
     */
    protected String[] getData(){
        return data;
    }
    
    /**
     * Returns the data type
     * @return
     */
    public DataType<T> getDataType(){
        return this.datatype;
    }
    
    /**
     * Returns the default aggregate function
     * @return
     */
    public AggregateFunction<T> getDefaultFunction(){
        return this.function;
    }
    
    /**
     * Tells the implementing class to prepare the generalization process
     */
    protected abstract AbstractGroup[][] prepareGroups();
    
    /**
     * Is this builder prepared allready
     * @param prepared
     */
    protected void setPrepared(boolean prepared){
        this.prepared = prepared;
        if (prepared == false) {
            this.abstractGroups = null;
        }
    }
}