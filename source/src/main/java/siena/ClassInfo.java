/*
 * Copyright 2009 Alberto Gimeno <gimenete at gmail.com>
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package siena;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

import siena.core.lifecycle.LifeCyclePhase;
import siena.core.lifecycle.LifeCycleUtils;
import siena.embed.Embedded;

public class ClassInfo {
	
	protected static Map<Class<?>, ClassInfo> infoClasses = new ConcurrentHashMap<Class<?>, ClassInfo>();
	
	public Class<?> clazz;
	
	public String tableName;

	public List<Field> keys = new ArrayList<Field>();
	public List<Field> insertFields = new ArrayList<Field>();
	public List<Field> updateFields = new ArrayList<Field>();
	public List<Field> generatedKeys = new ArrayList<Field>();
	public List<Field> allFields = new ArrayList<Field>();
	public List<Field> joinFields = new ArrayList<Field>();

	public Map<LifeCyclePhase, List<Method>> lifecycleMethods = new HashMap<LifeCyclePhase, List<Method>>();
	
	protected ClassInfo(Class<?> clazz) {
		this.clazz = clazz;
		tableName = getTableName(clazz);

        Field[] fields = Util.getFields(clazz);

		for (Field field : fields) {
			
			Class<?> type = field.getType();
			if(type == Class.class || type == Query.class ||
					(field.getModifiers() & Modifier.TRANSIENT) == Modifier.TRANSIENT ||
					(field.getModifiers() & Modifier.STATIC) == Modifier.STATIC ||
					field.isSynthetic()){
				continue;
			}
					
			Id id = field.getAnnotation(Id.class);
			if(id != null) {
				// ONLY long ID can be auto_incremented
				if(id.value() == Generator.AUTO_INCREMENT 
						&& ( Long.TYPE == type || Long.class.isAssignableFrom(type))) {
					generatedKeys.add(field);
				} else {
					insertFields.add(field);
				}
				keys.add(field);
			} 
			else {
				updateFields.add(field);
				insertFields.add(field);
			}
			
			if(field.getAnnotation(Join.class) != null){
				if (!ClassInfo.isModel(field.getType())){
					throw new SienaException("Join not possible: Field "+field.getName()+" is not a relation field");
				}
				else joinFields.add(field);
			}
			allFields.add(field);
		}
		
		for(Method m : clazz.getDeclaredMethods()){
			List<LifeCyclePhase> lcps = LifeCycleUtils.getMethodLifeCycles(m);
			for(LifeCyclePhase lcp: lcps){
				List<Method> methods = lifecycleMethods.get(lcp);
				if(methods == null){
					methods = new ArrayList<Method>();
					lifecycleMethods.put(lcp, methods);
				}
				methods.add(m);
			}
		}
	}

	private String getTableName(Class<?> clazz) {
		Table t = clazz.getAnnotation(Table.class);
		if(t == null) return clazz.getSimpleName();
		return t.value();
	}

	public List<String> getUpdateFieldsColumnNames() {
		List<String> strs = new ArrayList<String>(this.updateFields.size());
		for(Field field: this.updateFields){
			Column c = field.getAnnotation(Column.class);
			if(c != null && c.value().length > 0) {
				strs.add(c.value()[0]);
			}
			
			// default mapping: field names
			else if(isModel(field.getType())) {
				ClassInfo ci = getClassInfo(field.getType());
				for (Field key : ci.keys) {
					Collections.addAll(strs, getColumnNames(key));
				}
			}
			else {
				strs.add(field.getName());
			}
		}
		return strs;
	}
	
	public static String[] getColumnNames(Field field) {
		Column c = field.getAnnotation(Column.class);
		if(c != null && c.value().length > 0) return c.value();
		
		// default mapping: field names
		if(isModel(field.getType())) {
			ClassInfo ci = getClassInfo(field.getType());
			List<String> keys = new ArrayList<String>();
			// if no @column is provided
			// if the model has one single key, we use the local field name
			// if the model has several keys, we concatenate the fieldName+"_"+keyName
			if(ci.keys.size()==1){
				return new String[] { field.getName() };
			}
			for (Field key : ci.keys) {
				// uses the prefix fieldName_ to prevent problem with models having the same field names
				keys.addAll(Arrays.asList(getColumnNamesWithPrefix(key, field.getName()+"_")));
			}
			return keys.toArray(new String[keys.size()]);
		}
		return new String[]{ field.getName() };
	}

	public static String[] getColumnNamesWithPrefix(Field field, String prefix) {
		Column c = field.getAnnotation(Column.class);
		if(c != null && c.value().length > 0) {
			String[] cols = c.value();
			for(int i=0;i<cols.length;i++){
				cols[i]=prefix+cols[i];
			}
			return cols;
		}
		
		// default mapping: field names
		if(isModel(field.getType())) {
			ClassInfo ci = getClassInfo(field.getType());
			List<String> keys = new ArrayList<String>();
			// if no @column is provided
			// if the model has one single key, we use the local field name
			// if the model has several keys, we concatenate the fieldName+"_"+keyName
			if(ci.keys.size()==1){
				return new String[] { field.getName() };
			}
			for (Field key : ci.keys) {
				// concatenates prefix with new prefix
				keys.addAll(Arrays.asList(getColumnNamesWithPrefix(key, prefix+field.getName()+"_")));
			}
			return keys.toArray(new String[keys.size()]);
		}
		return new String[]{ prefix + field.getName() };
	}
	
	public static String[] getColumnNames(Field field, String tableName) {
		Column c = field.getAnnotation(Column.class);
		if(c != null && c.value().length > 0) {
			if(tableName!=null && !("".equals(tableName))){
				String[] cols = c.value();
				for(int i=0;i<cols.length;i++){
					cols[i]=tableName+"."+cols[i];
				}
				return cols;
			}
			else return c.value();
		}
		
		// default mapping: field names
		if(isModel(field.getType())) {
			ClassInfo ci = getClassInfo(field.getType());
			List<String> keys = new ArrayList<String>();
			// if no @column is provided
			// if the model has one single key, we use the local field name
			// if the model has several keys, we concatenate the fieldName+"_"+keyName
			if(ci.keys.size()==1){
				return new String[] { field.getName() };
			}
			for (Field key : ci.keys) {
				keys.addAll(Arrays.asList(getColumnNamesWithPrefix(key, field.getName()+"_")));
			}
			return keys.toArray(new String[keys.size()]);
		}
		if(tableName!=null && !("".equals(tableName)))
			return new String[]{ tableName+"."+field.getName() };
		else return new String[]{ field.getName() };
	}
	
	public static boolean isModel(Class<?> type) {
		// this way is much better in Java syntax
		if(Model.class.isAssignableFrom(type)) /*if(type.getSuperclass() == Model.class)*/
			return true;
		// TODO: this needs to be tested
		if(type.getName().startsWith("java.")) return false;
		
		/*if(type == Json.class)*/
		if(Json.class.isAssignableFrom(type)) return false;
		return !ClassInfo.getClassInfo(type).keys.isEmpty();
	}

	public static boolean isId(Field field) {
		return field.getAnnotation(Id.class) != null;
	}
	
	public static boolean isEmbedded(Field field) {
		return field.getAnnotation(Embedded.class) != null;
	}
	
	/**
	 * Useful for those PersistenceManagers that only support one @Id
	 * @param clazz
	 * @return
	 */
	public static Field getIdField(Class<?> clazz) {
		List<Field> keys = ClassInfo.getClassInfo(clazz).keys;
		if(keys.isEmpty())
			throw new SienaException("No valid @Id defined in class "+clazz.getName());
		if(keys.size() > 1)
			throw new SienaException("Multiple @Id defined in class "+clazz.getName());
		return keys.get(0);
	}

	/**
	 * Useful for those PersistenceManagers that only support one @Id
	 * @param clazz
	 * @return
	 */
	public Field getIdField() {
		if(keys.isEmpty())
			throw new SienaException("No valid @Id defined in class "+tableName);
		if(keys.size() > 1)
			throw new SienaException("Multiple @Id defined in class "+tableName);
		return keys.get(0);
	}
	
	public static ClassInfo getClassInfo(Class<?> clazz) {
		ClassInfo ci = infoClasses.get(clazz);
		if(ci == null) {
			ci = new ClassInfo(clazz);
			infoClasses.put(clazz, ci);
		}
		return ci;
	}

	public List<Method> getLifeCycleMethod(LifeCyclePhase lcp){
		return lifecycleMethods.get(lcp);
	}

}
