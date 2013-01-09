package org.cyclopsgroup.jmxterm.cmd;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;

import org.cyclopsgroup.jcli.annotation.Cli;
import org.cyclopsgroup.jcli.annotation.Option;
import org.cyclopsgroup.jmxterm.Command;
import org.cyclopsgroup.jmxterm.Session;
import org.json.simple.JSONObject;

/**
 * Dump all attributes to JSON
 *
 * @author <a href="mailto:remi@datadoghq.com">Remi Hakim</a>
 */
@Cli( name = "dump", description = "Display a JSON Formatted dictionnary of all the attributes and their values of all MBeans of the specified domain or of all domains if domain is not specified.", note = "Without any parameter, it dumps all the domains.")

public class DumpCommand
    extends Command
{
    private String domain;
    private String valuesOnly="false";
    
    /**
     * @inheritDoc
     */
    @Override
    public void execute()
        throws IOException, JMException
    {   
    	JSONObject dump = new JSONObject();
    	Session session = getSession();
        MBeanServerConnection con = session.getConnection().getServerConnection();
        List<String> beans = new ArrayList<String>();
        
        if ( domain == null && session.getDomain() != null)
        {
            domain = session.getDomain();
        }
        beans = BeansCommand.getBeans(session, domain);
        
        for ( String name : beans )
        {
        	JSONObject attr = getBeanAttributes(new ObjectName( name ), session, con);
        	if ( attr.size() != 0)
        	{
        		dump.put(name, attr);
        	}
        }
        StringWriter out = new StringWriter();
        dump.writeJSONString(out);
        String jsonText = out.toString();
        session.output.println(jsonText);
        
    }

    private JSONObject getBeanAttributes(ObjectName name, Session session, MBeanServerConnection con) throws InstanceNotFoundException, IntrospectionException, ReflectionException, IOException {
        JSONObject result = new JSONObject();
        MBeanAttributeInfo[] ais = con.getMBeanInfo( name ).getAttributes();
        List<String> att = new ArrayList<String>();
        for ( MBeanAttributeInfo ai : ais )
        {
            if ( ai.isReadable() ) 
            {
                att.add(ai.getName());
            }
        }
        try
        {
            AttributeList attrList = con.getAttributes(name, att.toArray(new String[att.size()]));
            for (Object attr : attrList)
            {
                Attribute at = (Attribute)attr;
                Object val = jsonify(at.getValue());
                if ( val != null)
                {
                    result.put(at.getName(), val);
                }

            }
        }
        catch (Exception e)
        {   
            for (String attributeName : att)
            {
                try{
                    Object attr = con.getAttribute( name, attributeName );
                    Object val = jsonify(attr);
                    if ( val != null )
                    {
                        result.put(attributeName, val);
                    }
                }
                catch (Exception f)
                {
                    continue;
                }
            }
        }

        return result;   
    }
    
    /**
     * Converts an attribute to an object that can be put into a JSON object with the relevant value 
     *
     * @param value The object to JSONify
     * @return An object that can be put to a JSONObject
     */    
    private Object jsonify(Object value)
    {
    
        if (this.valuesOnly.equals("true")) {
            if ( value instanceof String || value instanceof Boolean || value instanceof javax.management.ObjectName)
            {
                return null;
            }
            
        }
        
        
        if ( value==null || value instanceof Double || value instanceof Number || value instanceof Float || value instanceof Boolean || value instanceof String)
        {
            return value;
        }
        if ( value.getClass().isArray() )
        {
            LinkedList<Object> list = new LinkedList<Object>();
            for ( int i = 0; i < Array.getLength( value ); i++ )
            {
                Object val = jsonify(Array.get( value, i ));
                if (val != null)
                {
                    list.add(val);
                }
            }
            if (list.size() == 0)
            {
            	return null;
            }
            return list;
        }
        
        else if ( Collection.class.isAssignableFrom( value.getClass() ) )
        {
            LinkedList<Object> list = new LinkedList<Object>();   
            for ( Object obj : ( (Collection<?>) value ) )
            {
                Object val = jsonify(obj);
                if ( val != null)
                {
                    list.add(val);
                }
            }
            
            if ( list.size() == 0 )
            {
            	return null;
            }
            return list;
        }
        else if ( Map.class.isAssignableFrom( value.getClass() ) )
        {
            JSONObject obj = new JSONObject();
                
            for ( Map.Entry<?, ?> entry : ( (Map<?, ?>) value ).entrySet() )
            {
                Object key = jsonify(entry.getKey());
                Object mapValue = jsonify(entry.getValue());
                if ( mapValue != null )
                {
                    obj.put(key, mapValue);
                }
            }
            
            if ( obj.size() == 0 )
            {
            	return null;
            }
            
            return obj;
            
        }
        
        else if ( CompositeData.class.isAssignableFrom( value.getClass() ) )
        {
            CompositeData data = (CompositeData) value;
            JSONObject obj = new JSONObject();
            for ( Object key : data.getCompositeType().keySet() )
            {
                Object v = jsonify(data.get( (String) key ));
                if ( v != null)
                {
                    obj.put(key, jsonify(v));
                }
            }
            if ( obj.size() == 0 )
            {
            	return null;
            }
            return obj;
        }

        return value.toString();
        
    }
 

    /**
     * Set domain option
     *
     * @param domain Domain option to set
     */
    @Option( name = "d" , longName = "domain", description = "Domain name" )
    public final void setDomain( String domain )
    {
        this.domain = domain;
    }
    
    /**
     * Allow to dump only numeric values
     *
     * @param values Set to "true" if you want to dump only numeric values, to any other value otherwise
     */
    @Option( name = "v", longName = "values", description = "values Only")
    public final void setValues( String valuesOnly )
    {
        
        this.valuesOnly = valuesOnly;
    }
}
