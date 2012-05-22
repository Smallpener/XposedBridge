package de.robv.android.xposed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.WeakHashMap;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.reflect.MemberUtils;
import org.apache.commons.lang3.reflect.MethodUtils;

public class XposedHelpers {
	private static final HashMap<String, Field> fieldCache = new HashMap<String, Field>();
	private static final HashMap<String, Method> methodCache = new HashMap<String, Method>();
	private static final HashMap<String, Constructor<?>> constructorCache = new HashMap<String, Constructor<?>>();
	private static final WeakHashMap<Object, HashMap<String, Object>> additionalFields = new WeakHashMap<Object, HashMap<String, Object>>();
	
	/**
	 * Look up a field in a class and set it to accessible. The result is cached.
	 * If the field was not found, a {@link NoSuchFieldError} will be thrown.
	 */
	public static Field findField(Class<?> clazz, String fieldName) {
		return findField(false, clazz, fieldName);
	}
	
	/**
	 * @see #findField(Class, String)
	 */
	public static Field findField(boolean suppressErrorLogging, Class<?> clazz, String fieldName) {
		StringBuilder sb = new StringBuilder(clazz.getName());
		sb.append('#');
		sb.append(fieldName);
		String fullFieldName = sb.toString();
		
		if (fieldCache.containsKey(fullFieldName)) {
			Field field = fieldCache.get(fullFieldName);
			if (field == null)
				throw new NoSuchFieldError(fullFieldName);
			return field;
		}
		
		try {
			Field field = clazz.getDeclaredField(fieldName);
			field.setAccessible(true);
			fieldCache.put(fullFieldName, field);
			return field;
		} catch (NoSuchFieldException e) {
			if (!suppressErrorLogging)
				XposedBridge.log(e);
			fieldCache.put(fullFieldName, null);
			throw new NoSuchFieldError(fullFieldName);
		}
	}
	
	/**
	 * Look up a method in a class and set it to accessible. The result is cached.
	 * If the method was not found, a {@link NoSuchMethodError} will be thrown.
	 */
	public static Method findMethodExact(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
		return findMethodExact(false, clazz, methodName, parameterTypes);
	}
	
	/**
	 * @see #findMethodExact(Class, String, Class...)
	 */
	public static Method findMethodExact(boolean suppressErrorLogging, Class<?> clazz, String methodName, Class<?>... parameterTypes) {
		StringBuilder sb = new StringBuilder(clazz.getName());
		sb.append('#');
		sb.append(methodName);
		sb.append(getParametersString(parameterTypes));
		sb.append("#exact");
		String fullMethodName = sb.toString();
		
		if (methodCache.containsKey(fullMethodName)) {
			Method method = methodCache.get(fullMethodName);
			if (method == null)
				throw new NoSuchMethodError(fullMethodName);
			return method;
		}
		
		try {
			Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
			method.setAccessible(true);
			methodCache.put(fullMethodName, method);
			return method;
		} catch (NoSuchMethodException e) {
			if (!suppressErrorLogging)
				XposedBridge.log(e);
			methodCache.put(fullMethodName, null);
			throw new NoSuchMethodError(fullMethodName);
		}
	}
	
	/**
	 * Look up a method in a class and set it to accessible. The result is cached.
	 * This does not only look for exact matches, but for the closest match. 
	 * If the method was not found, a {@link NoSuchMethodError} will be thrown.
	 */
	public static Method findMethodBestMatch(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
		return findMethodBestMatch(false, clazz, methodName, parameterTypes);
	}
	

	/**
	 * @see #findMethodBestMatch(Class, String, Class...)
	 * @see MethodUtils#getMatchingAccessibleMethod
	 */
	public static Method findMethodBestMatch(boolean suppressErrorLogging, Class<?> clazz, String methodName, Class<?>... parameterTypes) {
		StringBuilder sb = new StringBuilder(clazz.getName());
		sb.append('#');
		sb.append(methodName);
		sb.append(getParametersString(parameterTypes));
		sb.append("#bestmatch");
		String fullMethodName = sb.toString();
		
		if (methodCache.containsKey(fullMethodName)) {
			Method method = methodCache.get(fullMethodName);
			if (method == null)
				throw new NoSuchMethodError(fullMethodName);
			return method;
		}
		
		try {
			Method method = findMethodExact(true, clazz, methodName, parameterTypes);
			methodCache.put(fullMethodName, method);
			return method;
		} catch (NoSuchMethodError ignored) {}
		
		Method bestMatch = null;
		Method[] methods = clazz.getDeclaredMethods();
		for (Method method : methods) {
		    // compare name and parameters
			if (method.getName().equals(methodName) && ClassUtils.isAssignable(parameterTypes, method.getParameterTypes(), true)) {
			    // get accessible version of method
	            if (bestMatch == null || MemberUtils.compareParameterTypes(
						method.getParameterTypes(),
						bestMatch.getParameterTypes(),
						parameterTypes) < 0) {
            		bestMatch = method;
	            }
		    }
		}
		
		if (bestMatch != null) {
			bestMatch.setAccessible(true);
			methodCache.put(fullMethodName, bestMatch);
			return bestMatch;
		} else {
			NoSuchMethodError e = new NoSuchMethodError(fullMethodName);
			if (!suppressErrorLogging)
				XposedBridge.log(e);
			methodCache.put(fullMethodName, null);
			throw e;
		}
	}
	
	/**
	 * Look up a method in a class and set it to accessible. Parameter types are
	 * determined from the <code>args</code> for the method call. The result is cached.
	 * This does not only look for exact matches, but for the closest match. 
	 * If the method was not found, a {@link NoSuchMethodError} will be thrown.
	 */
	public static Method findMethodBestMatch(Class<?> clazz, String methodName, Object... args) {
		return findMethodBestMatch(false, clazz, methodName, getParameterTypes(args));
	}
	
	/**
	 * Look up a method in a class and set it to accessible. Parameter types are
	 * preferably taken from the <code>parameterTypes</code>. Any item in this array that
	 * is <code>null</code> is determined from the corresponding item in <code>args</code>.
	 * The result is cached.
	 * This does not only look for exact matches, but for the closest match. 
	 * If the method was not found, a {@link NoSuchMethodError} will be thrown.
	 */
	public static Method findMethodBestMatch(Class<?> clazz, String methodName, Class<?>[] parameterTypes, Object[] args) {
		Class<?>[] argsClasses = null;
		for (int i = 0; i < parameterTypes.length; i++) {
			if (parameterTypes[i] != null)
				continue;
			if (argsClasses == null)
				argsClasses = getParameterTypes(args);
			parameterTypes[i] = argsClasses[i];
		}
		return findMethodBestMatch(false, clazz, methodName, parameterTypes);
	}
	
	/**
	 * Return an array with the classes of the given objects
	 */
	public static Class<?>[] getParameterTypes(Object... args) {
		Class<?>[] clazzes = new Class<?>[args.length];
		for (int i = 0; i < args.length; i++) {
			clazzes[i] = (args[i] != null) ? args[i].getClass() : null;
		}
		return clazzes;
	}
	
	/**
	 * Return an array with the classes of the given objects
	 */
	public static Class<?>[] getClassesAsArray(Class<?>... clazzes) {
		return clazzes;
	}
	
	private static String getParametersString(Class<?>... clazzes) {
		StringBuilder sb = new StringBuilder('(');
		boolean first = true;
		for (Class<?> clazz : clazzes) {
			if (first)
				first = false;
			else
				sb.append(',');
			
			if (clazz != null)
				sb.append(clazz.getCanonicalName());
			else
				sb.append("null");
		}
		sb.append(')');
		return sb.toString();
	}
	
	
	public static Constructor<?> findConstructorExact(Class<?> clazz, Class<?>... parameterTypes) {
		return findConstructorExact(false, clazz, parameterTypes);
	}
	
	public static Constructor<?> findConstructorExact(boolean suppressErrorLogging, Class<?> clazz, Class<?>... parameterTypes) {
		StringBuilder sb = new StringBuilder(clazz.getName());
		sb.append(getParametersString(parameterTypes));
		sb.append("#exact");
		String fullConstructorName = sb.toString();
		
		if (constructorCache.containsKey(fullConstructorName)) {
			Constructor<?> constructor = constructorCache.get(fullConstructorName);
			if (constructor == null)
				throw new NoSuchMethodError(fullConstructorName);
			return constructor;
		}
		
		try {
			Constructor<?> constructor = clazz.getDeclaredConstructor(parameterTypes);
			constructor.setAccessible(true);
			constructorCache.put(fullConstructorName, constructor);
			return constructor;
		} catch (NoSuchMethodException e) {
			if (!suppressErrorLogging)
				XposedBridge.log(e);
			constructorCache.put(fullConstructorName, null);
			throw new NoSuchMethodError(fullConstructorName);
		}
	}
	
	public static Constructor<?> findConstructorBestMatch(Class<?> clazz, Class<?>... parameterTypes) {
		return findConstructorBestMatch(false, clazz, parameterTypes);
	}
	

	public static Constructor<?> findConstructorBestMatch(boolean suppressErrorLogging, Class<?> clazz, Class<?>... parameterTypes) {
		StringBuilder sb = new StringBuilder(clazz.getName());
		sb.append(getParametersString(parameterTypes));
		sb.append("#bestmatch");
		String fullConstructorName = sb.toString();
		
		if (constructorCache.containsKey(fullConstructorName)) {
			Constructor<?> constructor = constructorCache.get(fullConstructorName);
			if (constructor == null)
				throw new NoSuchMethodError(fullConstructorName);
			return constructor;
		}
		
		try {
			Constructor<?> constructor = findConstructorExact(true, clazz, parameterTypes);
			constructorCache.put(fullConstructorName, constructor);
			return constructor;
		} catch (NoSuchMethodError ignored) {}
		
		Constructor<?> bestMatch = null;
		Constructor<?>[] constructors = clazz.getDeclaredConstructors();
		for (Constructor<?> constructor : constructors) {
		    // compare name and parameters
			if (ClassUtils.isAssignable(parameterTypes, constructor.getParameterTypes(), true)) {
			    // get accessible version of method
	            if (bestMatch == null || MemberUtils.compareParameterTypes(
	            		constructor.getParameterTypes(),
						bestMatch.getParameterTypes(),
						parameterTypes) < 0) {
            		bestMatch = constructor;
	            }
		    }
		}
		
		if (bestMatch != null) {
			bestMatch.setAccessible(true);
			constructorCache.put(fullConstructorName, bestMatch);
			return bestMatch;
		} else {
			NoSuchMethodError e = new NoSuchMethodError(fullConstructorName);
			if (!suppressErrorLogging)
				XposedBridge.log(e);
			constructorCache.put(fullConstructorName, null);
			throw e;
		}
	}
	
	public static Constructor<?> findConstructorBestMatch(Class<?> clazz, Object... args) {
		return findConstructorBestMatch(false, clazz, getParameterTypes(args));
	}
	
	public static Constructor<?> findConstructorBestMatch(Class<?> clazz, Class<?>[] parameterTypes, Object[] args) {
		Class<?>[] argsClasses = null;
		for (int i = 0; i < parameterTypes.length; i++) {
			if (parameterTypes[i] != null)
				continue;
			if (argsClasses == null)
				argsClasses = getParameterTypes(args);
			parameterTypes[i] = argsClasses[i];
		}
		return findConstructorBestMatch(false, clazz, parameterTypes);
	}
	
	//#################################################################################################
	public static void setObjectField(Object obj, String fieldName, Object value) {
		try {
			findField(obj.getClass(), fieldName).set(obj, value);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static void setBooleanField(Object obj, String fieldName, boolean value) {
		try {
			findField(obj.getClass(), fieldName).setBoolean(obj, value);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static void setByteField(Object obj, String fieldName, byte value) {
		try {
			findField(obj.getClass(), fieldName).setByte(obj, value);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static void setCharField(Object obj, String fieldName, char value) {
		try {
			findField(obj.getClass(), fieldName).setChar(obj, value);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static void setDoubleField(Object obj, String fieldName, double value) {
		try {
			findField(obj.getClass(), fieldName).setDouble(obj, value);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static void setFloatField(Object obj, String fieldName, float value) {
		try {
			findField(obj.getClass(), fieldName).setFloat(obj, value);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}

	public static void setIntField(Object obj, String fieldName, int value) {
		try {
			findField(obj.getClass(), fieldName).setInt(obj, value);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static void setLongField(Object obj, String fieldName, long value) {
		try {
			findField(obj.getClass(), fieldName).setLong(obj, value);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static void setShortField(Object obj, String fieldName, short value) {
		try {
			findField(obj.getClass(), fieldName).setShort(obj, value);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	//#################################################################################################
	public static Object getObjectField(Object obj, String fieldName) {
		try {
			return findField(obj.getClass(), fieldName).get(obj);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static boolean getBooleanField(Object obj, String fieldName) {
		try {
			return findField(obj.getClass(), fieldName).getBoolean(obj);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static byte getByteField(Object obj, String fieldName) {
		try {
			return findField(obj.getClass(), fieldName).getByte(obj);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static char getCharField(Object obj, String fieldName) {
		try {
			return findField(obj.getClass(), fieldName).getChar(obj);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static double getDoubleField(Object obj, String fieldName) {
		try {
			return findField(obj.getClass(), fieldName).getDouble(obj);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static float getFloatField(Object obj, String fieldName) {
		try {
			return findField(obj.getClass(), fieldName).getFloat(obj);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static int getIntField(Object obj, String fieldName) {
		try {
			return findField(obj.getClass(), fieldName).getInt(obj);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static long getLongField(Object obj, String fieldName) {
		try {
			return findField(obj.getClass(), fieldName).getLong(obj);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static short getShortField(Object obj, String fieldName) {
		try {
			return findField(obj.getClass(), fieldName).getShort(obj);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}

	//#################################################################################################
	public static void setStaticObjectField(Class<?> clazz, String fieldName, Object value) {
		try {
			findField(clazz, fieldName).set(null, value);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static void setStaticBooleanField(Class<?> clazz, String fieldName, boolean value) {
		try {
			findField(clazz, fieldName).setBoolean(null, value);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static void setStaticByteField(Class<?> clazz, String fieldName, byte value) {
		try {
			findField(clazz, fieldName).setByte(null, value);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static void setStaticCharField(Class<?> clazz, String fieldName, char value) {
		try {
			findField(clazz, fieldName).setChar(null, value);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static void setStaticDoubleField(Class<?> clazz, String fieldName, double value) {
		try {
			findField(clazz, fieldName).setDouble(null, value);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static void setStaticFloatField(Class<?> clazz, String fieldName, float value) {
		try {
			findField(clazz, fieldName).setFloat(null, value);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}

	public static void setStaticIntField(Class<?> clazz, String fieldName, int value) {
		try {
			findField(clazz, fieldName).setInt(null, value);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static void setStaticLongField(Class<?> clazz, String fieldName, long value) {
		try {
			findField(clazz, fieldName).setLong(null, value);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static void setStaticShortField(Class<?> clazz, String fieldName, short value) {
		try {
			findField(clazz, fieldName).setShort(null, value);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	//#################################################################################################
	public static Object getStaticObjectField(Class<?> clazz, String fieldName) {
		try {
			return findField(clazz, fieldName).get(null);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static boolean getStaticBooleanField(Class<?> clazz, String fieldName) {
		try {
			return findField(clazz, fieldName).getBoolean(null);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static byte getStaticByteField(Class<?> clazz, String fieldName) {
		try {
			return findField(clazz, fieldName).getByte(null);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static char getStaticCharField(Class<?> clazz, String fieldName) {
		try {
			return findField(clazz, fieldName).getChar(null);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static double getStaticDoubleField(Class<?> clazz, String fieldName) {
		try {
			return findField(clazz, fieldName).getDouble(null);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static float getStaticFloatField(Class<?> clazz, String fieldName) {
		try {
			return findField(clazz, fieldName).getFloat(null);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static int getStaticIntField(Class<?> clazz, String fieldName) {
		try {
			return findField(clazz, fieldName).getInt(null);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static long getStaticLongField(Class<?> clazz, String fieldName) {
		try {
			return findField(clazz, fieldName).getLong(null);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	public static short getStaticShortField(Class<?> clazz, String fieldName) {
		try {
			return findField(clazz, fieldName).getShort(null);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		}
	}
	
	//#################################################################################################
	/**
	 * Call instance or static method <code>methodName</code> for object <code>obj</code> with the arguments
	 * <code>args</code>. The types for the arguments will be determined automaticall from <code>args</code>
	 */
	public static Object callMethod(Object obj, String methodName, Object... args) {
		try {
			return findMethodBestMatch(obj.getClass(), methodName, args).invoke(obj, args);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		} catch (InvocationTargetException e) {
			throw new InvocationTargetError(e.getCause());
		}
	}
	
	/**
	 * Call instance or static method <code>methodName</code> for object <code>obj</code> with the arguments
	 * <code>args</code>. The types for the arguments will be taken from <code>parameterTypes</code>.
	 * This array can have items that are <code>null</code>. In this case, the type for this parameter
	 * is determined from <code>args</code>.
	 */
	public static Object callMethod(Object obj, String methodName, Class<?>[] parameterTypes, Object... args) {
		try {
			return findMethodBestMatch(obj.getClass(), methodName, parameterTypes, args).invoke(obj, args);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		} catch (InvocationTargetException e) {
			throw new InvocationTargetError(e.getCause());
		}
	}
	
	/**
	 * Call static method <code>methodName</code> for class <code>clazz</code> with the arguments
	 * <code>args</code>. The types for the arguments will be determined automaticall from <code>args</code>
	 */
	public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
		try {
			return findMethodBestMatch(clazz, methodName, args).invoke(null, args);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		} catch (InvocationTargetException e) {
			throw new InvocationTargetError(e.getCause());
		}
	}
	
	/**
	 * Call static method <code>methodName</code> for class <code>clazz</code> with the arguments
	 * <code>args</code>. The types for the arguments will be taken from <code>parameterTypes</code>.
	 * This array can have items that are <code>null</code>. In this case, the type for this parameter
	 * is determined from <code>args</code>.
	 */
	public static Object callStaticMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes, Object... args) {
		try {
			return findMethodBestMatch(clazz, methodName, parameterTypes, args).invoke(null, args);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		} catch (InvocationTargetException e) {
			throw new InvocationTargetError(e.getCause());
		}
	}
	
	public static class InvocationTargetError extends Error {
		private static final long serialVersionUID = -1070936889459514628L;
		public InvocationTargetError(Throwable cause) {
			super(cause);
		}
	}
	
	//#################################################################################################
	public static Object newInstance(Class<?> clazz, Object... args) {
		try {
			return findConstructorBestMatch(clazz, args).newInstance(args);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		} catch (InvocationTargetException e) {
			throw new InvocationTargetError(e.getCause());
		} catch (InstantiationException e) {
			throw new InstantiationError(e.getMessage());
		}
	}
	
	public static Object newInstance(Class<?> clazz, Class<?>[] parameterTypes, Object... args) {
		try {
			return findConstructorBestMatch(clazz, parameterTypes, args).newInstance(args);
		} catch (IllegalAccessException e) {
			// should not happen
			XposedBridge.log(e);
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			XposedBridge.log(e);
			throw e;
		} catch (InvocationTargetException e) {
			throw new InvocationTargetError(e.getCause());
		} catch (InstantiationException e) {
			throw new InstantiationError(e.getMessage());
		}
	}
	
	//#################################################################################################	
	public static void setAdditionalInstanceField(Object obj, String key, Object value) {
		if (obj == null)
			throw new NullPointerException("object must not be null");
		if (key == null)
			throw new NullPointerException("key must not be null");
		
		HashMap<String, Object> objectFields = additionalFields.get(obj);
		if (objectFields == null) {
			objectFields = new HashMap<String, Object>();
			additionalFields.put(obj, objectFields);
		}
		
		objectFields.put(key, value);
	}
	
	public static Object getAdditionalInstanceField(Object obj, String key) {
		if (obj == null)
			throw new NullPointerException("object must not be null");
		if (key == null)
			throw new NullPointerException("key must not be null");
		
		HashMap<String, Object> objectFields = additionalFields.get(obj);
		if (objectFields == null)
			return null;
		
		return objectFields.get(key);
	}
	
	public static void setAdditionalStaticField(Object obj, String key, Object value) {
		setAdditionalInstanceField(obj.getClass(), key, value);
	}
	
	public static Object getAdditionalStaticField(Object obj, String key) {
		return getAdditionalInstanceField(obj.getClass(), key);
	}
	
	public static void setAdditionalStaticField(Class<?> clazz, String key, Object value) {
		setAdditionalInstanceField(clazz, key, value);
	}
	
	public static Object getAdditionalStaticField(Class<?> clazz, String key) {
		return getAdditionalInstanceField(clazz, key);
	}
}
