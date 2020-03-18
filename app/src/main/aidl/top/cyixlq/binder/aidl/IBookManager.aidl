package top.cyixlq.binder.aidl;

import top.cyixlq.binder.aidl.Book;
import top.cyixlq.binder.aidl.IOnNewBookArrivedListener;

interface IBookManager {
    List<Book> getBookList();
    void addBook(in Book book);
    void registerListener(IOnNewBookArrivedListener listener);
    void unregisterListener(IOnNewBookArrivedListener listener);
}
