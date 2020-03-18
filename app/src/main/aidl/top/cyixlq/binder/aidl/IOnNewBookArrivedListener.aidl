package top.cyixlq.binder.aidl;

import top.cyixlq.binder.aidl.Book;

interface IOnNewBookArrivedListener {
    void onNewBookArrived(in Book newBook);
}
