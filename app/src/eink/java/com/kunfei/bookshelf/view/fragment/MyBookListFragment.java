package com.kunfei.bookshelf.view.fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.kunfei.basemvplib.BitIntentDataManager;
import com.kunfei.bookshelf.DbHelper;
import com.kunfei.bookshelf.MApplication;
import com.kunfei.bookshelf.R;
import com.kunfei.bookshelf.base.MBaseFragment;
import com.kunfei.bookshelf.bean.BookShelfBean;
import com.kunfei.bookshelf.help.ItemTouchCallback;
import com.kunfei.bookshelf.presenter.BookDetailPresenter;
import com.kunfei.bookshelf.presenter.MyBookListPresenter;
import com.kunfei.bookshelf.presenter.ReadBookPresenter;
import com.kunfei.bookshelf.presenter.contract.MyBookListContract;
import com.kunfei.bookshelf.utils.DensityUtil;
import com.kunfei.bookshelf.utils.NetworkUtils;
import com.kunfei.bookshelf.view.activity.BookDetailActivity;
import com.kunfei.bookshelf.view.activity.MyReadBookActivity;
import com.kunfei.bookshelf.view.adapter.BookShelfAdapter;
import com.kunfei.bookshelf.view.adapter.BookShelfListAdapter;
import com.kunfei.bookshelf.view.adapter.base.OnItemClickListenerTwo;
import com.kunfei.bookshelf.widget.popuplist.PopupList;
import com.kunfei.bookshelf.widget.recycler.SpaceItemDecoration;
import com.kunfei.bookshelf.view.adapter.MyBookShelfGridAdapter;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

public class MyBookListFragment extends MBaseFragment<MyBookListContract.Presenter> implements MyBookListContract.View {

    @BindView(R.id.refresh_layout)
    SwipeRefreshLayout refreshLayout;
    @BindView(R.id.local_book_rv_content)
    RecyclerView rvBookshelf;
    @BindView(R.id.tv_empty)
    TextView tvEmpty;
    @BindView(R.id.rl_empty_view)
    RelativeLayout rlEmptyView;

    private Unbinder unbinder;
    private String bookPx;
    private boolean resumed = false;
    private boolean isRecreate;
    private int group;

    private BookShelfAdapter bookShelfAdapter;


    private List<String> bookPopMenu = new ArrayList<>();



    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            resumed = savedInstanceState.getBoolean("resumed");
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    public int createLayoutId() {
        return R.layout.my_fragment_book_list;
    }

    @Override
    protected MyBookListContract.Presenter initInjector() {
        return new MyBookListPresenter();
    }

    @Override
    protected void initData() {
        CallBackValue callBackValue = (CallBackValue) getActivity();
        bookPx = preferences.getString(getString(R.string.pk_bookshelf_px), "0");
        isRecreate = callBackValue != null && callBackValue.isRecreate();

        bookPopMenu = new ArrayList<>();

        bookPopMenu.add("移出书架");
        bookPopMenu.add("清除缓存");
        bookPopMenu.add("书籍详情");

    }




    @Override
    protected void bindView() {
        super.bindView();
        unbinder = ButterKnife.bind(this, view);
        if (preferences.getBoolean("bookshelfIsList", false)) {
            rvBookshelf.setLayoutManager(new LinearLayoutManager(getContext()));
            bookShelfAdapter = new BookShelfListAdapter(getActivity());
        } else {
            rvBookshelf.setLayoutManager(new GridLayoutManager(getContext(), 3));
            bookShelfAdapter = new MyBookShelfGridAdapter(getActivity());
        }
        rvBookshelf.setAdapter((RecyclerView.Adapter) bookShelfAdapter);
        int bookshelfSpace = MApplication.getConfigPreferences().getInt(MApplication.getInstance().getString(R.string.pk_bookshelf_space), 10);

        rvBookshelf.addItemDecoration(new SpaceItemDecoration(DensityUtil.dp2px(getActivity(),bookshelfSpace)));
        refreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorAccent));
    }

    @Override
    protected void firstRequest() {
        group = preferences.getInt("bookshelfGroup", 0);
        if (preferences.getBoolean(getString(R.string.pk_auto_refresh), false)
                && !isRecreate && NetworkUtils.isNetWorkAvailable() && group != 2) {
            mPresenter.queryBookShelf(true, group);
        } else {
            mPresenter.queryBookShelf(false, group);
        }
    }

    @Override
    protected void bindEvent() {
        refreshLayout.setOnRefreshListener(() -> {
            mPresenter.queryBookShelf(NetworkUtils.isNetWorkAvailable(), group);
            if (!NetworkUtils.isNetWorkAvailable()) {
                Toast.makeText(getContext(), "无网络，请打开网络后再试。", Toast.LENGTH_SHORT).show();
            }
            refreshLayout.setRefreshing(false);
        });
        ItemTouchCallback itemTouchHelpCallback = new ItemTouchCallback();
        itemTouchHelpCallback.setSwipeRefreshLayout(refreshLayout);
        if (bookPx.equals("2")) {
            itemTouchHelpCallback.setDragEnable(true);
            ItemTouchHelper itemTouchHelper = new ItemTouchHelper(itemTouchHelpCallback);
            itemTouchHelper.attachToRecyclerView(rvBookshelf);
        } else {
            itemTouchHelpCallback.setDragEnable(false);
            ItemTouchHelper itemTouchHelper = new ItemTouchHelper(itemTouchHelpCallback);
            itemTouchHelper.attachToRecyclerView(rvBookshelf);
        }
        bookShelfAdapter.setItemClickListener(getAdapterListener());
        itemTouchHelpCallback.setOnItemTouchCallbackListener(bookShelfAdapter.getItemTouchCallbackListener());

    }

    private OnItemClickListenerTwo getAdapterListener() {
        return new OnItemClickListenerTwo() {
            @Override
            public void onClick(View view, int index) {
                BookShelfBean bookShelfBean = bookShelfAdapter.getBooks().get(index);
                bookShelfBean.setHasUpdate(false);
                DbHelper.getDaoSession().getBookShelfBeanDao().insertOrReplace(bookShelfBean);
                Intent intent = new Intent(getActivity(), MyReadBookActivity.class);
                intent.putExtra("openFrom", ReadBookPresenter.OPEN_FROM_APP);
                String key = String.valueOf(System.currentTimeMillis());
                intent.putExtra("bookKey", key);
                try {
                    BitIntentDataManager.getInstance().putData(key, bookShelfBean.clone());
                } catch (Exception e) {
                    BitIntentDataManager.getInstance().putData(key, bookShelfBean);
                    e.printStackTrace();
                }
                startActivityByAnim(intent, android.R.anim.fade_in, android.R.anim.fade_out);
            }

            @Override
            public void onLongClick(View view, int index) {
				int[] location = new int[2];
            	view.getLocationOnScreen(location);
           	 	PopupList popupList = new PopupList(view.getContext());
            	popupList.showPopupListWindow(view,1, location[0] + view.getWidth() / 2,
                    location[1], bookPopMenu, new PopupList.PopupListListener() {
                        @Override
                        public boolean showPopupList(View adapterView, View contextView, int contextPosition) {
                            return true;
                        }

                        @Override
                        public void onPopupListClick(View contextView, int contextPosition, int position) {

	                        
                            if(position==0) {//移出书架

                                 mPresenter.removeFromBookShelf(bookShelfAdapter.getBooks().get(index));
                                 
                            }else if (position==1){//清除缓存
                                mPresenter.clearCaches(bookShelfAdapter.getBooks().get(index));
                                //;

                            }else if (position==2){//书籍详情
				                Intent intent = new Intent(getActivity(), BookDetailActivity.class);
				                intent.putExtra("openFrom", BookDetailPresenter.FROM_BOOKSHELF);
				                String key = String.valueOf(System.currentTimeMillis());
				                intent.putExtra("data_key", key);
				                BitIntentDataManager.getInstance().putData(key, bookShelfAdapter.getBooks().get(index));

				                startActivityByAnim(intent, android.R.anim.fade_in, android.R.anim.fade_out);

                            }
                            //Toast.makeText(contextView.getContext(), contextPosition + "," + position, Toast.LENGTH_SHORT).show();
                        }
                    });
                    
	            



                

            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        if (resumed) {
            resumed = false;
            stopBookShelfRefreshAnim();
        }
    }

    @Override
    public void onPause() {
        resumed = true;
        super.onPause();
    }

    private void stopBookShelfRefreshAnim() {
        if (bookShelfAdapter.getBooks() != null && bookShelfAdapter.getBooks().size() > 0) {
            for (BookShelfBean bookShelfBean : bookShelfAdapter.getBooks()) {
                if (bookShelfBean.isLoading()) {
                    bookShelfBean.setLoading(false);
                    refreshBook(bookShelfBean.getNoteUrl());
                }
            }
        }
    }

    @Override
    public void refreshBookShelf(List<BookShelfBean> bookShelfBeanList) {

        bookShelfAdapter.replaceAll(bookShelfBeanList, bookPx);
        if (rlEmptyView == null) return;
        if (bookShelfBeanList.size() > 0) {
            rlEmptyView.setVisibility(View.GONE);
        } else {
            tvEmpty.setText(R.string.bookshelf_empty);
            rlEmptyView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void refreshBook(String noteUrl) {
        bookShelfAdapter.refreshBook(noteUrl);
    }

    @Override
    public void updateGroup(Integer group) {
        this.group = group;
    }

    @Override
    public void refreshError(String error) {

    }

    @Override
    public SharedPreferences getPreferences() {
        return preferences;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }

    public interface CallBackValue {
        boolean isRecreate();

        int getGroup();
    }

}
