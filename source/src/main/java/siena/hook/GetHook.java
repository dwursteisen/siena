package siena.hook;

import siena.SienaException;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Created by IntelliJ IDEA.
 * User: david
 * Date: 02/05/11
 * Time: 13:16
 * To change this template use File | Settings | File Templates.
 */
public class GetHook implements OnHook {


    @Override
    public void doBefore(Object object) {

        Class<DoBefore> annotation = DoBefore.class;

        Method[] methods = object.getClass().getDeclaredMethods();
        for(Method m : methods) {
            DoBefore annotationOnMethod = m.getAnnotation(annotation);

            if(annotationOnMethod == null) {
                continue;
            }

            Hook[] on = annotationOnMethod.on();
            Arrays.sort(on);
            int hookType = Arrays.binarySearch(on, Hook.GET);
            if(hookType >= 0) {
                try {
                    m.invoke(object);
                } catch (IllegalAccessException e) {
                    throw new SienaException(e);
                } catch (InvocationTargetException e) {
                    throw new SienaException(e);
                }
            }

        }
    }

    @Override
    public void doAfter(Object object) {
         Class<DoAfter> annotation = DoAfter.class;

        Method[] methods = object.getClass().getDeclaredMethods();
        for(Method m : methods) {
            DoAfter annotationOnMethod = m.getAnnotation(annotation);

            if(annotationOnMethod == null) {
                continue;
            }

            Hook[] on = annotationOnMethod.on();
            Arrays.sort(on);
            int hookType = Arrays.binarySearch(on, Hook.GET);
            if(hookType >= 0) {
                try {
                    m.invoke(object);
                } catch (IllegalAccessException e) {
                    throw new SienaException(e);
                } catch (InvocationTargetException e) {
                    throw new SienaException(e);
                }
            }

        }
    }
}
