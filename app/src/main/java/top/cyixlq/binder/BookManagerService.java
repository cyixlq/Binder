package top.cyixlq.binder;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import top.cyixlq.binder.aidl.Book;
import top.cyixlq.binder.aidl.IBookManager;
import top.cyixlq.binder.aidl.IOnNewBookArrivedListener;

public class BookManagerService extends Service {

    private static final String TAG = "BMS";
    private AtomicBoolean mIsServiceDestoryed = new AtomicBoolean(false);
    // private CopyOnWriteArrayList<IOnNewBookArrivedListener> mListenerList = new CopyOnWriteArrayList<>();
    // 使用RemoteCallbackList后的代码
    private RemoteCallbackList<IOnNewBookArrivedListener> mListenerList = new RemoteCallbackList<>();

    /**
     * <p>“AIDL中能够使用的List只有ArrayList，但是我们这里却使用了CopyOnWriteArrayList（注意它不是继承自ArrayList），
     * 为什么能够正常工作呢？这是因为AIDL中所支持的是抽象的List，而List只是一个接口，
     * 因此虽然服务端返回的是CopyOnWriteArrayList，
     * 但是在Binder中会按照List的规范去访问数据并最终形成一个新的ArrayList传递给客户端。
     * 所以，我们在服务端采用CopyOnWriteArrayList是完全可以的。和此类似的还有ConcurrentHashMap，读者可以体会一下这种转换情形。”
     * </p>
     * 摘录来自: 任玉刚. “Android开发艺术探索。” iBooks.
     */
    private CopyOnWriteArrayList<Book> mBookList = new CopyOnWriteArrayList<>();

    private Binder mBinder = new IBookManager.Stub() {
        @Override
        public List<Book> getBookList() {
            return mBookList;
        }

        @Override
        public void addBook(Book book) {
            mBookList.add(book);
        }

        @Override
        public void registerListener(IOnNewBookArrivedListener listener) {
//            if (!mListenerList.contains(listener)) {
//                mListenerList.add(listener);
//            } else {
//                Log.i(TAG, "listener already exists");
//            }
//            Log.i(TAG, "registerListener,size:" + mListenerList.size());
            // 使用RemoteCallbackList后的代码
            mListenerList.register(listener);
            Log.i(TAG, "registerListener,size:" + mListenerList.getRegisteredCallbackCount());
        }

        @Override
        public void unregisterListener(IOnNewBookArrivedListener listener) {
//            if(mListenerList.contains(listener)) {
//                mListenerList.remove(listener);
//                Log.i(TAG,"unregister listener succeed.");
//            } else {
//                Log.i(TAG,"not found,can not unregister.");
//            }
//            Log.i(TAG,"unregisterListener,current size:" + mListenerList.size());
            // 使用RemoteCallbackList后的代码
            mListenerList.unregister(listener);
            Log.i(TAG,"unregisterListener,current size:" + mListenerList.getRegisteredCallbackCount());
            // 使用RemoteCallbackList后就能成功反注册，Logcat->BMS: unregisterListener,current size:0
            /*
             * Logcat:
             * BMS: not found,can not unregister.
             * BMS: unregisterListener,current size:1
             *
             * 多进程中出现无法反注册，
             * 因为Binder会把客户端传递过来的对象重新转化并生成一个新的对象。
             * 虽然我们在注册和解注册过程中使用的是同一个客户端对象，但是通过Binder传递到服务端后，却会产生两个全新的对象。
             * 别忘了对象是不能跨进程直接传输的，对象的跨进程传输本质上都是反序列化的过程，
             * 这就是为什么AIDL中的自定义对象都必须要实现Parcelable接口的原因。
             * 如果需要实现反注册，那么就需要用到RemoteCallbackList。
             * RemoteCallbackList是系统专门提供的用于删除跨进程listener的接口：
             * public class RemoteCallbackList<E extends IInterface>
             * 同时RemoteCallbackList还有一个很有用的功能，那就是当客户端进程终止后，它能够自动移除客户端所注册的listener。
             * 另外，RemoteCallbackList内部自动实现了线程同步的功能，所以我们使用它来注册和解注册时，不需要做额外的线程同步工作。
             */
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mBookList.add(new Book(1, "Android"));
        mBookList.add(new Book(2, "Ios"));
        new Thread(new ServiceWorker()).start();
    }

    public BookManagerService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private void onNewBookArrived(Book book) throws RemoteException {
        mBookList.add(book);
//        Log.i(TAG,"onNewBookArrived,notify listeners:" + mListenerList.
//                size());
//        for (int i = 0; i < mListenerList.size(); i++) {
//            IOnNewBookArrivedListener listener = mListenerList.get(i);
//            Log.i(TAG,"onNewBookArrived,notify listener:" + listener);
//            listener.onNewBookArrived(book);
//        }
        // 使用RemoteCallbackList后的代码
        /*
         * 使用RemoteCallbackList，有一点需要注意，我们无法像操作List一样去操作它，尽管它的名字中也带个List，
         * 但是它并不是一个List。遍历RemoteCallbackList，必须要按照下面的方式进行，
         * 其中beginBroadcast和finishBroadcast必须要配对使用，
         * 哪怕我们仅仅是想要获取RemoteCallbackList中的元素个数，这是必须要注意的地方。
         */
        final int size = mListenerList.beginBroadcast();
        for (int i = 0; i < size; i++) {
            IOnNewBookArrivedListener l = mListenerList.getBroadcastItem(i);
            if (l != null) {
                try {
                    l.onNewBookArrived(book);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        mListenerList.finishBroadcast();
    }
    private class ServiceWorker implements Runnable {
        @Override
        public void run() {
            while (!mIsServiceDestoryed.get()) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                int bookId = mBookList.size() + 1;
                Book newBook = new Book(bookId,"new book#" + bookId);
                try {
                    onNewBookArrived(newBook);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        mIsServiceDestoryed.set(true);
        super.onDestroy();
    }
}
