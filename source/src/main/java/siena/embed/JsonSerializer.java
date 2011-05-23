package siena.embed;

import static siena.Json.list;
import static siena.Json.map;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import siena.Json;
import siena.SienaException;
import siena.Util;

public class JsonSerializer {
	
	public static Json serialize(Object obj) {
		return serialize(obj, null);
	}
	
	public static Json serialize(Object obj, Field f) {
		if(obj == null) return new Json(null);
		
		Class<?> clazz = obj.getClass();
		
		//if(obj instanceof Map<?, ?>) {
		if(Map.class.isAssignableFrom(clazz)){
			Map<?, ?> map = (Map<?, ?>) obj;
			Json result = map();
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				String key = entry.getKey().toString();
				Json value = serialize(entry.getValue(), null);
				result.put(key, value);
			}
			return result;
		}

		//if(obj instanceof Collection<?>) {
		if(Collection.class.isAssignableFrom(clazz)){
			Json result = list();
			Collection<?> col = (Collection<?>) obj;
			for (Object object : col) {
				result.add(serialize(object));
			}
			return result;
		}

		if(Json.class.isAssignableFrom(clazz)){
			return new Json(obj);
		}
		
		try {
			EmbeddedList list = obj.getClass().getAnnotation(EmbeddedList.class);
			if(list != null) {
				return serializeList(obj);
			}
			EmbeddedMap map = obj.getClass().getAnnotation(EmbeddedMap.class);
			if(map != null) {
				return serializeMap(obj);
			}
		} catch(SienaException e) {
			throw e;
		} catch(Exception e) {
			throw new SienaException(e);
		}
		
		if(f != null) {
			Format format = f.getAnnotation(Format.class);
			if(format != null) {
				if(obj.getClass() == Date.class) {
					Date date = (Date) obj;
					SimpleDateFormat sdf = new SimpleDateFormat(format.value());
					return new Json(sdf.format(date));
				}
			}
		}
		
		return new Json(obj);
	}
	
	private static Json serializeMap(Object obj) throws Exception {
		Field[] fields = Util.getFields(obj.getClass());
		Json result = map();
		for (Field f : fields) {
			if(mustIgnore(f)) continue;
			
			Key k = f.getAnnotation(Key.class);
			if(k != null) {
				result.put(k.value(), serialize(f.get(obj), f));
			} else {
				result.put(f.getName(), serialize(f.get(obj), f));
			}
		}
		
		// TEST
		// serializes super classes
		Class<?> clazz = obj.getClass().getSuperclass();
		while(clazz!=null){
			fields = Util.getFields(clazz);
			for (Field f : fields) {
				if(mustIgnore(f)) continue;
				
				Key k = f.getAnnotation(Key.class);
				if(k != null) {
					result.put(k.value(), serialize(Util.readField(obj, f), f));
				} else {
					result.put(f.getName(), serialize(Util.readField(obj, f), f));
				}
			}
			clazz = clazz.getSuperclass();
		}
		// TEST
		return result;
	}
	
	private static Json serializeList(Object obj) throws Exception {
        Field[] fields = Util.getFields(obj.getClass());
		Json result = list();
		for (Field f : fields) {
			if(mustIgnore(f)) continue;
			
			At at = f.getAnnotation(At.class);
			if(at == null) throw new SienaException("Field "+obj.getClass()+"."+f.getName()+" must be annotated with @At(n)");
			result.addAt(at.value(), serialize(f.get(obj), f));
		}
		
		// TEST
		// serializes super classes
		Class<?> clazz = obj.getClass().getSuperclass();
		while(clazz!=null){
			fields = Util.getFields(clazz);
			for (Field f : fields) {
				if(mustIgnore(f)) continue;
				
				At at = f.getAnnotation(At.class);
				if(at == null) throw new SienaException("Field "+obj.getClass()+"."+f.getName()+" must be annotated with @At(n)");
				result.addAt(at.value(), serialize(f.get(obj), f));
			}
			clazz = clazz.getSuperclass();
		}
		// TEST
		return result;
	}
	
	private static Class<?> getGenericClass(Field f, int n) {
		Type genericFieldType = f.getGenericType();
		if(genericFieldType instanceof ParameterizedType){
		    ParameterizedType aType = (ParameterizedType) genericFieldType;
		    Type[] fieldArgTypes = aType.getActualTypeArguments();
		    return (Class<?>) fieldArgTypes[n];
		}
		return null;
	}
	
	public static Object deserialize(Class<?> clazz, Json data) {
		try {
			EmbeddedMap map = clazz.getAnnotation(EmbeddedMap.class);
			if(map != null) {
				if(!data.isMap()) {
					throw new SienaException("Error while deserializating class "+clazz
							+". A Json map is needed but found: "+data);
				}
				Object obj = Util.createObjectInstance(clazz);
				Field[] fields = Util.getFields(clazz);
				for (Field f : fields) {
					if(mustIgnore(f)) continue;
					
					Key key = f.getAnnotation(Key.class);
					if(key != null)
						Util.setField(obj, f, deserialize(f, data.get(key.value())));
					else
						Util.setField(obj, f, deserialize(f, data.get(f.getName())));
				}
				
				// deserializes super classes
				Class<?> superclazz = obj.getClass().getSuperclass();
				while(superclazz!=null){
					fields = Util.getFields(superclazz);
					for (Field f : fields) {
						if(mustIgnore(f)) continue;
						
						Key key = f.getAnnotation(Key.class);
						if(key != null)
							Util.setField(obj, f, deserialize(f, data.get(key.value())));
						else
							Util.setField(obj, f, deserialize(f, data.get(f.getName())));
					}
					superclazz = superclazz.getSuperclass();
				}
				return obj;
			}
			
			EmbeddedList list = clazz.getAnnotation(EmbeddedList.class);
			if(list != null) {
				if(!data.isList()) {
					throw new SienaException("Error while deserializating class "+clazz
							+". A Json list is needed but found: "+data);
				}
				Object obj = Util.createObjectInstance(clazz);
				Field[] fields = Util.getFields(clazz);
				for (Field f : fields) {
					if(mustIgnore(f)) continue;
					
					At at = f.getAnnotation(At.class);
					if(at == null) throw new SienaException("Field "+obj.getClass()+"."+f.getName()+" must be annotated with @At(n)");
					Json value = data.at(at.value());
					Util.setField(obj, f, deserialize(f, value));
				}
				
				// deserializes super classes
				Class<?> superclazz = obj.getClass().getSuperclass();
				while(superclazz!=null){
					fields = Util.getFields(superclazz);
					for (Field f : fields) {
						if(mustIgnore(f)) continue;
						
						At at = f.getAnnotation(At.class);
						if(at == null) throw new SienaException("Field "+obj.getClass()+"."+f.getName()+" must be annotated with @At(n)");
						Json value = data.at(at.value());
						Util.setField(obj, f, deserialize(f, value));
					}
					superclazz = superclazz.getSuperclass();
				}
				return obj;
			}
			if(Json.class.isAssignableFrom(clazz)){
				return data;
			}
			return deserializePlain(clazz, data);
		} catch(Exception e) {
			throw new SienaException(e);
		}
	}
	
	private static boolean mustIgnore(Field field) {
		boolean b = (field.getModifiers() & Modifier.TRANSIENT) == Modifier.TRANSIENT ||
			(field.getModifiers() & Modifier.STATIC) == Modifier.STATIC || 
			field.isSynthetic();
		
		if(!field.isAccessible())
			field.setAccessible(true);
		
		return b;
	}
	
	public static Object deserialize(Field f, Json data) {
		if(data == null || data.isNull()) return deserializePlain(f.getType(), data);
		
		Class<?> clazz = f.getType();
		if(Map.class.isAssignableFrom(clazz)) {
			if(!data.isMap()) {
				throw new SienaException("Error while deserializating field "+f.getDeclaringClass()
						+"."+f.getName()+" of type "+clazz
						+". A Json map is needed but found: "+data);
			}
			Map<String, Object> map = new HashMap<String, Object>();
			for (String key : data.keys()) {
				map.put(key, deserialize(getGenericClass(f, 1), data.get(key)));
			}
			return map;
		}
		else if(Collection.class.isAssignableFrom(clazz)) {
			if(!data.isList()) {
				throw new SienaException("Error while deserializating field "+f.getDeclaringClass()
						+"."+f.getName()+" of type "+clazz
						+". A Json list is needed but found: "+data);
			}
			Collection<Object> collection = null;
			if(clazz == List.class) {
				collection = new ArrayList<Object>(data.size());
			} else {
				collection = new HashSet<Object>();
			}
			for (Json value : data) {
				collection.add(deserialize(getGenericClass(f, 0), value));
			}
			return collection;
		}
		else if(Json.class.isAssignableFrom(clazz)){
			return data;
		}
		
		Format format = f.getAnnotation(Format.class);
		if(format != null) {
			if(f.getType() == Date.class) {
				SimpleDateFormat sdf = new SimpleDateFormat(format.value());
				try {
					return sdf.parse(data.str());
				} catch (ParseException e) {
					throw new SienaException(e);
				}
			}
		}
		
		return deserialize(clazz, data);
	}
	
	private static Object deserializePlain(Class<?> type, Json data) {
		if(Boolean.class == type || boolean.class == type) {
			return data!=null ? data.asBoolean() : 0;
		}
		if(type == Byte.class || type == Byte.TYPE)    {
			return data!=null ? data.asBoolean() : 0;
		}
		else if(type == Short.class || type == Short.TYPE)   {
			return data!=null ? data.asShort() : 0;
		}
		else if(type == Integer.class || type == Integer.TYPE) {
			return data!=null ? data.asInt() : 0;
		}
		else if(type == Long.class || type == Long.TYPE)    {
			return data!=null ? data.asLong() : 0;
		}
		else if(type == Float.class || type == Float.TYPE)   {
			return data!=null ? data.asFloat() : 0;
		}
		else if(type == Double.class || type == Double.TYPE)  {
			return data!=null ? data.asDouble() : 0;
		}
		else if(type == String.class)  {
			return data!=null ? data.str() : null;
		}
		else if(type.isEnum()) {
			return data!=null ? Enum.valueOf((Class<Enum>) type, data.str()) : null;
		}
		return null;
	}

}
