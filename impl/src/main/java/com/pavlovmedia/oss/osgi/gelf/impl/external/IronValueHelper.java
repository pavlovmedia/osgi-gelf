package com.pavlovmedia.oss.osgi.gelf.impl.external;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Vector;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Note: This class was copy-pasta from a different pavlov project
 * 
 * This class is dual purpose. You can use it as a one-off for converting
 * elements, or you can create an instance of it pointing at a map and
 * refer to things by their property name.
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
public class IronValueHelper {
    private final Map<String,Object> properties;
    
    public IronValueHelper(final Map<String,Object> properties) {
        this.properties = properties;
    }
    
    /**
     * This will return a type-safe conversion. If the property does not
     * exist, or is of the wrong type, it will return an empty Optional
     * 
     * @param propertyName
     * @param clazz
     * @return
     */
    public <T extends Object> Optional<T> getSafe(final String propertyName, final Class<T> clazz) {
        return properties.containsKey(propertyName)
                ? safeCast(properties.get(propertyName), clazz)
                : Optional.empty();
    }
    
    /**
     * This will get a Boolean by property name. If the type is not specifically a Boolean
     * it will use the string value of the object and parse it. If the property does 
     * not exist it will return an empty Optional
     * 
     * @param propertyName
     * @return
     */
    public Optional<Boolean> getBoolean(final String propertyName) {
        return properties.containsKey(propertyName) 
                ? Optional.of(getBoolean(properties.get(propertyName)))
                : Optional.empty();
    }
    
    /**
     * This will get an Integer by property name. If the type is not specifically an Integer
     * it will use the string value of the object and parse it. If the property does 
     * not exist or it is not parseable it will return an empty Optional
     * 
     * @param propertyName
     * @return
     */
    public Optional<Integer> getInteger(final String propertyName) {
        try {
        return properties.containsKey(propertyName) 
                ? Optional.of(getInteger(properties.get(propertyName)))
                : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
    
    /**
     * This will get a string from the property. If the object is not a
     * string, it will return the output of toString. If the property does
     * not exist, it will return an empty Optional
     * 
     * @param propertyName
     * @return
     */
    public Optional<String> getString(final String propertyName) {
        return properties.containsKey(propertyName) 
                ? Optional.of(getString(properties.get(propertyName)))
                : Optional.empty();
    }
    
    /**
     * See {@link #getStringList(Object)}
     * 
     * @param propertyName
     * @return
     */
    public List<String> getStringList(final String propertyName) {
        return getStringList(propertyName, s -> s);
    }
    
    /**
     * See {@link #getStringList(Object, Function)}
     * 
     * @param propertyName
     * @param reformatter
     * @return
     */
    public List<String> getStringList(final String propertyName, final Function<String,String> reformatter) {
        return properties.containsKey(propertyName)
                ? getStringList(properties.get(propertyName), reformatter)
                : Collections.emptyList();
    }
    
    /**
     * Does a safeCast of this object and if that fails, parses toString()
     * @param o
     * @return
     */
    public static Boolean getBoolean(final Object o) {
        return safeCast(o, Boolean.class).orElse(Boolean.parseBoolean(o.toString()));
    }
    
    /**
     * Does a safeCast to Integer, and if that fails parses toString().
     * Note: This can throw a parse exception.
     * 
     * @param o
     * @return
     * @throws NumberFormatException
     */
    public static Integer getInteger(final Object o) throws NumberFormatException {
        return safeCast(o, Integer.class).orElse(Integer.decode(o.toString()));
    }
    
    /**
     * Does a safeCast to String, and if that fails returns toString()
     * @param o
     * @return
     */
    public static String getString(final Object o) {
        return safeCast(o, String.class).orElse(o.toString());
    }
    
    /**
     * This will return a type-safe conversion. If the type is not correct
     * it returns an empty Optional.
     * 
     * @param o
     * @param clazz
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T extends Object> Optional<T> safeCast(final Object o, final Class<T> clazz) {
        if (clazz.isInstance(o)) {
            return Optional.of((T) o);
        }
        return Optional.empty();
    }
    
    /**
     * This will turn an entry into a list of strings. See {@link #getStringList(Object, Function)}
     * for more details.
     * 
     * @param o
     * @return
     */
    public static List<String> getStringList(final Object o) {
        return getStringList(o, s -> s);
    }
    
    /**
     * This will turn an entry into a list of strings. Since OSGi (and iron) can do
     * this in so many ways, we check for String[], Vector<>, and a comma separated
     * string that starts with [ and ends with ].
     * 
     * If you are using cowboy + iron, you may need to also post-process the entry
     * before it goes into the list. To do so, you specify a reformatter. If you
     * don't need it, you can call {@link #getStringList(Object)} instead.
     * 
     * @param o
     * @param reformatter
     * @return
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static List<String> getStringList(final Object o, final Function<String,String> reformatter) {
        // OSGi is wonky with lists, then can be arrays, or vectors
        Optional<String[]> oArray = safeCast(o, String[].class);
        if (oArray.isPresent()) {
            return Arrays.asList(oArray.get());
        }
        
        Optional<Vector> oVector = safeCast(o, Vector.class);
        if (oVector.isPresent()) {
            return new ArrayList<String>((Vector<String>) oVector.get());
        }
        
        // Now iron gets funky, we may have wrapped a list in brackets
        String strValue = safeCast(o, String.class).orElse(o.toString());
        if (strValue.startsWith("[") && strValue.endsWith("]")) {
            return fastCsvSplit(strValue.substring(1, strValue.length() - 2))
                .stream()
                .map(String::trim)
                .map(reformatter)
                .collect(Collectors.toList());
        }
        
        // In the end an array with one entry
        return Arrays.asList(strValue);
    }
    
    /**
     * This will take a line of text and split it based on commas while
     * ignoring escaped double quotes
     * @param input
     * @return
     */
    public static List<String> fastCsvSplit(final String input) {
        ArrayList<String> ret = new ArrayList<>();
        int lastStart = 0;
        int curPos = 0;
        boolean inEscape = false;
        
        for (; curPos < input.length(); curPos++) {
            char cur = input.charAt(curPos);
            switch (cur) {
                case '"':
                    inEscape = !inEscape;
                    break;
                case ',':
                    if (!inEscape) {
                        ret.add(input.substring(lastStart, curPos));
                        lastStart=curPos+1;
                    }
                    break;
                default:
            }
        }
        
        if (lastStart != curPos) {
            ret.add(input.substring(lastStart, curPos));
        }
        
        return ret;
    }
}
