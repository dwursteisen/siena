package siena.hook;

/**
 * Created by IntelliJ IDEA.
 * User: david
 * Date: 02/05/11
 * Time: 13:16
 * To change this template use File | Settings | File Templates.
 */
public interface OnHook {

    void doBefore(Object object);
    void doAfter(Object object);
}
