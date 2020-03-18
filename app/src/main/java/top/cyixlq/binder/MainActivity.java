package top.cyixlq.binder;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import java.util.List;

import top.cyixlq.binder.aidl.Book;
import top.cyixlq.binder.aidl.IBookManager;
import top.cyixlq.binder.aidl.IOnNewBookArrivedListener;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int MESSAGE_NEW_BOOK_ARRIVED = 1;
    private IBookManager mRemoteBookManager;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mRemoteBookManager = IBookManager.Stub.asInterface(service);
            try {
                //需要注意的是，服务端的方法有可能需要很久才能执行完毕，这个时候下面的代码就会导致ANR
                List<Book> list = mRemoteBookManager.getBookList();
                //这里打印的list type是ArrayList，证实了BookManagerService中的那段文档注释
                Log.i(TAG, "查询书本列表,list type:" + list.getClass().
                        getCanonicalName());
                Log.i(TAG, "查询书本列表:" + list.toString());
                Book newBook = new Book(3, "Android开发艺术探索");
                mRemoteBookManager.addBook(newBook);
                Log.i(TAG, "add book:" + newBook);
                List<Book> newList = mRemoteBookManager.getBookList();
                Log.i(TAG, "query book list:" + newList.toString());
                mRemoteBookManager.registerListener(mOnNewBookArrivedListener);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mRemoteBookManager = null;
        }
    };

    private IOnNewBookArrivedListener mOnNewBookArrivedListener = new
            IOnNewBookArrivedListener.Stub() {
                @Override
                public void onNewBookArrived(Book newBook) {
                    mHandler.obtainMessage(MESSAGE_NEW_BOOK_ARRIVED, newBook)
                            .sendToTarget();
                }
            };


    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_NEW_BOOK_ARRIVED:
                    Log.i(TAG, "receive new book :" + msg.obj);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Intent intent = new Intent(this, BookManagerService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        if (mRemoteBookManager != null
                && mRemoteBookManager.asBinder().isBinderAlive()) {
            try {
                Log.i(TAG, "unregister listener:" + mOnNewBookArrivedListener);
                mRemoteBookManager.unregisterListener(mOnNewBookArrivedListener);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        unbindService(mConnection);
        super.onDestroy();
    }
}
