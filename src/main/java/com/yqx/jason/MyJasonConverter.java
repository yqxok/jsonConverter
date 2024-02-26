package com.yqx.jason;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static java.lang.Character.toUpperCase;

public class MyJasonConverter<T> implements JsonConverter<T> {

    private int i=0;
    public Object getMap(String json){
        char[] chars = json.toCharArray();
        String key=null;
        Object collection=null;
        if(chars[i]=='{')
            collection=new HashMap<String,Object>();
        else if(chars[i]=='[')
            collection=new ArrayList();
        i++;
        for(;i<chars.length;i++){
            //处理list
            if(chars[i]<='9'&&chars[i]>='0'){
                int num = getNum(chars, json);
                if(collection instanceof ArrayList list)
                    list.add(num);
            }else if(chars[i]=='\"'){
                String tmp=getString(chars,json);
                if(collection instanceof ArrayList list){
                    list.add(tmp);
                }else
                    key=tmp;
            }else if (chars[i]=='{'||chars[i]=='['){
                Object value = getMap(json);
                if(collection instanceof ArrayList list){
                    list.add(value);
                }
            }
            else if(chars[i]==':'){
                //处理map
                i++;
                if(chars[i]=='\"'){
                    String value=getString(chars,json);
                    if(collection instanceof HashMap map){
                        map.put(key,value);
                    }
                }else if(chars[i]=='{'||chars[i]=='['){
                    Object value = getMap(json);
                    if(collection instanceof HashMap map){
                        map.put(key,value);
                    }
                }else if(chars[i]<='9'&&chars[i]>='0'){
                    int num = getNum(chars, json);
                    if(collection instanceof HashMap map){
                        map.put(key,num);
                    }
                }
            } else if(chars[i]=='}'||chars[i]==']') {
                return collection;
            }

        }
        i=0;
        return collection;
    };
    private String getString(char[] chars,String json){
        int left=i+1;i++;
        while(chars[i]!='\"')
            i++;
        String value=json.substring(left,i);
        return value;
    }
    private int getNum(char[] chars,String json){
        int num=0;
        while (chars[i]<='9'&&chars[i]>='0'){
            num=num*10+chars[i]-'0';
            i++;
        }
        return num;
    }

    @Override
    public T toObject(String json,T target) {
        Map map =(Map) getMap(json);
        Class<?> aClass = target.getClass();
        target=(T) dfs(map, aClass, target);

        return target;
    }
    private Object dfs(Map map,Class aClass,Object target) {
        map.forEach((k,v)->{
            try {
                String key=(String) k;
                Field field = aClass.getDeclaredField(key);
                field.setAccessible(true);
                Class<?> fieldType = field.getType();
                if(v instanceof Map map1){
                    Class<?> type = field.getType();
                    Constructor<?> declaredConstructor = type.getDeclaredConstructor();
                    Object o = declaredConstructor.newInstance();
                    Object o1=dfs(map1,type,o);
                    field.set(target,o1);

                }else if(v instanceof List list){
                    ParameterizedType type =(ParameterizedType) field.getGenericType();//不是泛型的ParameterizedType对象
                    Collection instance = dfsList(list, fieldType,type);//希望返回一个集合对象给我，第二个参数是集合实例，第三个参数是实例的ParameterizedType类型
                    field.set(target,instance);
                } else {
                    field.set(target,v);
                }

            } catch (Exception e) {
                throw new RuntimeException(e);
            }


        });;
        return target;
    }//target表示集合对象,type表示集合里的泛型类型
    private Collection dfsList(List list,Class clazz,ParameterizedType type){
        Collection target=null;
        if (clazz.isAssignableFrom(List.class)) {
            target=new ArrayList(){};
        }else {
            target=new HashSet(){};
        }
        Collection finalTarget = target;
        list.forEach(e->{
            try {
                if(e instanceof Map map){//实体类型
                    //通过泛型类型创建的对象
                    Class argument =(Class) type.getActualTypeArguments()[0];//因为泛型类型没有泛型了，所以返回Class类型
                    Object instance = argument.getDeclaredConstructor().newInstance();
                    instance=dfs(map,argument,instance);
                   finalTarget.add(instance);
                }else if(e instanceof List list1){//集合类型
                    ParameterizedType argument =(ParameterizedType) type.getActualTypeArguments()[0];//获取泛型的ParameterizedType对象
                    Class rawType =(Class) argument.getRawType();
                    Collection instance=dfsList(list1,rawType,argument);
                    finalTarget.add(instance);
                }else {//基本类型
                    finalTarget.add(e);
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        return target;
    }

    @Override
    public String toJson(Object target) {


        String json1 = dfsToJson(target);
        return json1;
    }
    private String dfsToJson(Object target){
        String json="{";
        try {
            Class<?> aClass = target.getClass();
            Field[] fields = aClass.getDeclaredFields();
            for (Field field : fields) {
                String fieldName = field.getName();
                String methodName= biggerValue(fieldName);
                Method method = aClass.getDeclaredMethod("get" + methodName);
                Object value = method.invoke(target);
                json=json+"\""+fieldName+"\""+":";
                String specialStr=null;
                if(isBaseClass(value)){
                    json=json+value+",";
                }else if((specialStr= specialClass(value))!=null){
                    json=json+"\""+specialStr+"\""+",";
                }else if(value instanceof String s){
                    json=json+"\""+s+"\""+",";
                }else if (value instanceof Collection collection){
                    json=json+dfsToJsonCollection(collection)+",";
                }
                else{
                    String s = dfsToJson(value);
                    json=json+s+",";
                }
            }
            json = json.substring(0,json.length()-1);
            json=json+"}";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return json;
    }
    private String dfsToJsonCollection(Collection collection){

        String str="[";
        String specialStr=null;
        for (Object o : collection) {
            if(isBaseClass(o)){
                str=str+o+",";
            }else if((specialStr= specialClass(o))!=null){
                str=str+"\""+specialStr+"\""+",";
            }else if(o instanceof String s){
                str=str+"\""+o+"\""+",";
            }else if(o instanceof Collection collection1){
                str=str+dfsToJsonCollection(collection1)+",";
            }else{
                str=str+dfsToJson(o)+",";
            }
        }
        str = str.substring(0,str.length()-1);
        str=str+"]";
        return str;
    }
    private boolean isBaseClass(Object o){
        if(o instanceof Integer)
            return true;
        else if (o instanceof Long)
            return true;
        else if(o instanceof Short)
            return true;
        else if(o instanceof Double)
            return true;
        else if(o instanceof Float)
            return true;
        return false;
    }
    public String specialClass(Object o){
        if(o instanceof LocalDateTime localDateTime){

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return formatter.format(localDateTime);
        }else if(o instanceof LocalDate localDate){
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            return formatter.format(localDate);
        }else if(o instanceof LocalTime localTime){
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            return formatter.format(localTime);
        }
        return null;
    }
    private String biggerValue(String s){
        char[] chars = s.toCharArray();
        chars[0] = toUpperCase(chars[0]);
        return String.valueOf(chars);


    }
}
