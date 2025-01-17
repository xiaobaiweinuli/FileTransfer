package com.example.filetransfer;

import android.Manifest;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.filetransfer.domain.DefaultConfig;
import com.example.filetransfer.manager.KeepLiveManager;
import com.example.filetransfer.service.WebService;
import com.example.filetransfer.utils.ClipBoardUtil;
import com.example.filetransfer.utils.IpUtil;
import com.example.filetransfer.utils.MyToast;

import java.io.File;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MainActivity
 *
 * @author uchiha.ben
 * @version 1.0
 * @date 2021/09/08
 */
public class MainActivity extends AppCompatActivity {
    private static final int PERMISSIONS_REQUEST_CODE = 454;
    private static final String[] PERMISSIONS = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};

    private long mExitTime;
    private TextView tvPath;
    private TextView tvAddress;
    private ListView listview;
    private File currentParent;
    private File[] currentFiles;
    private SwipeRefreshLayout srfl;
    private ImageView ivCreateFolder;
    private ImageView ivDefaultFolder;
    private ImageView ivBrowser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 检查全部权限
        if (!checkPermissions()) {
            ActivityCompat.requestPermissions(this,
                    PERMISSIONS, PERMISSIONS_REQUEST_CODE);
        }

        /*// 校验悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(MainActivity.this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + MainActivity.this.getPackageName()));
                startActivity(intent);
            }
        }
        // 1像素且透明Activity提升App进程优先级
        KeepLiveManager.getInstance().registerKeepLiveReceiver(this);*/

        // 初始化界面
        initView();

        // 启动Web服务
        WebService.start(getBaseContext());
    }

    /**
     * 初始化界面
     */
    private void initView() {
        tvPath = findViewById(R.id.tvPath);
        tvAddress = findViewById(R.id.tvAddress);
        listview = findViewById(R.id.listview);
        srfl = findViewById(R.id.srfl);
        ivCreateFolder = findViewById(R.id.ivCreateFolder);
        ivDefaultFolder = findViewById(R.id.ivDefaultFolder);
        ivBrowser = findViewById(R.id.ivBrowser);

        initFolderData();

        srfl.setColorSchemeResources(R.color.black);
        tvAddress.setText(getWebUrl());
        tvAddress.setOnClickListener(v -> {
            openWithBrowser();
        });
        listview.setOnItemClickListener((arg0, arg1, arg2, arg3) -> {
            if (arg2 == 0) {
                if (currentParent.getParentFile().isDirectory() && !currentParent.getAbsolutePath().equals(
                        Environment.getExternalStorageDirectory()
                                .getAbsolutePath())) {
                    currentParent = currentParent.getParentFile();
                    if (currentParent.listFiles() == null) {
                        currentFiles = new File[]{};
                    } else {
                        currentFiles = currentParent.listFiles();
                    }
                    inflateListView(currentFiles);
                }
            } else {
                arg2 = arg2 - 1;
                if (currentFiles[arg2].isDirectory()) {
                    File[] mfiles = currentFiles[arg2].listFiles();
                    if (mfiles == null) {
                        mfiles = new File[]{};
                    }
                    currentParent = currentFiles[arg2];
                    currentFiles = mfiles;
                    inflateListView(currentFiles);
                } else {
                    shareFile(currentFiles[arg2]);
                }
            }
        });
        srfl.setOnRefreshListener(() -> new Handler().postDelayed(() -> {
            refreshFolderData();
            srfl.setRefreshing(false);
        }, 500));
        ivCreateFolder.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("新建目录");
            final EditText editText = new EditText(this);
            editText.setSingleLine();
            builder.setView(editText);
            builder.setPositiveButton("确定", (dialog, which) -> {
                try {
                    Field mShowing = Dialog.class.getDeclaredField("mShowing");
                    mShowing.setAccessible(true);
                    String folderName = editText.getText().toString();
                    if (!TextUtils.isEmpty(folderName)) {
                        File folderPath = new File(currentParent.getAbsolutePath(), folderName);
                        folderPath.mkdirs();
                        refreshFolderData();
                        MyToast.makeText(MainActivity.this, "新建成功");
                        mShowing.set(dialog, true);
                    } else {
                        MyToast.makeText(MainActivity.this, "目录不能为空");
                        mShowing.set(dialog, false);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            builder.setNegativeButton("取消", (dialog, which) -> {
                try {
                    Field mShowing = Dialog.class.getDeclaredField("mShowing");
                    mShowing.setAccessible(true);
                    mShowing.set(dialog, true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            builder.setCancelable(false);
            builder.show();
        });
        ivDefaultFolder.setOnClickListener(v -> {
            DefaultConfig defaultConfig = DefaultConfig.getInstance();
            final String[] items = {"私有模式", "开放模式"};
            AlertDialog.Builder singleChoiceDialog = new AlertDialog.Builder(MainActivity.this);
            singleChoiceDialog.setIcon(R.drawable.icon);
            singleChoiceDialog.setTitle("默认路径:" + currentParent.getAbsolutePath());
            AtomicReference<Boolean> atomicReference = new AtomicReference(Boolean.FALSE);
            singleChoiceDialog.setSingleChoiceItems(items, 0, (dialog, which) -> atomicReference.set(which == 0 ? Boolean.FALSE : Boolean.TRUE));
            singleChoiceDialog.setPositiveButton("确定", (dialog, which) -> {
                defaultConfig.setDefaultFolderPath(currentParent.getAbsolutePath());
                defaultConfig.setPublicMode(atomicReference.get());
                MyToast.makeText(this, "设置成功");
            });
            singleChoiceDialog.setNegativeButton("取消", null);
            singleChoiceDialog.setCancelable(false);
            singleChoiceDialog.show();
        });
        ivBrowser.setOnClickListener(v -> {
            openWithBrowser();
        });
    }

    /**
     * 初始化默认文件夹内容展示
     */
    private void initFolderData() {
        File root = new File(DefaultConfig.getInstance().getDefaultFolderPath());
        if (!root.exists() || !root.isDirectory()) {
            root.mkdirs();
        }
        currentParent = root;
        File[] files = root.listFiles();
        List<File> tmpList = new ArrayList<>();
        if (files != null && files.length > 0) {
            tmpList.addAll(Arrays.asList(files));
        }
        Collections.sort(tmpList, (lhs, rhs) -> lhs.lastModified() - rhs.lastModified() > 0 ? 1 : 0);
        currentFiles = tmpList.toArray(new File[tmpList.size()]);
        inflateListView(currentFiles);
    }

    /**
     * 刷新当前文件夹内容展示
     */
    private void refreshFolderData() {
        currentFiles = currentParent.listFiles();
        inflateListView(currentFiles);
        tvAddress.setText(getWebUrl());
    }

    /**
     * 文件分享
     *
     * @param file
     */
    private void shareFile(File file) {
        if (null != file && file.exists()) {
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
            sendIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            sendIntent.setType("application/octet-stream");
            startActivity(Intent.createChooser(sendIntent, "分享文件"));
        } else {
            Toast.makeText(this, "分享文件不存在", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 初始化ListView
     *
     * @param files
     */
    private void inflateListView(File[] files) {
        final List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Map<String, Object> root = new HashMap<String, Object>();
        root.put("icon", R.drawable.folder);
        root.put("rootName", "../");
        list.add(root);
        for (int i = 0; i < files.length; i++) {
            Map<String, Object> map = new HashMap<String, Object>();
            if (files[i].isDirectory()) {
                map.put("icon", R.drawable.folder);
            } else {
                map.put("icon", R.drawable.file);
            }
            map.put("filename", files[i].getName());
            map.put("lastModified", sdf.format(new Date(files[i].lastModified())));
            list.add(map);
        }
        SimpleAdapter adapter = new SimpleAdapter(MainActivity.this, list,
                R.layout.item, new String[]{"icon", "rootName", "filename", "lastModified"}, new int[]{
                R.id.icon, R.id.root_name, R.id.file_name, R.id.last_modified});
        listview.setAdapter(adapter);
        tvPath.setText(currentParent.getAbsolutePath());
    }

    /**
     * 动态权限校验
     *
     * @return
     */
    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(getBaseContext(), permission)
                        != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 浏览器打开WEB端
     */
    private void openWithBrowser() {
        String url = tvAddress.getText().toString();
        ClipBoardUtil.copy(this, url);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    /**
     * 获取WEB端链接地址
     *
     * @return
     */
    private String getWebUrl() {
        return "http://" + IpUtil.getIPAddress(this) + ":" + DefaultConfig.getInstance().getDefaultPort();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 反注册防止内存泄漏
        KeepLiveManager.getInstance().unregisterKeepLiveReceiver(this);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            if ((System.currentTimeMillis() - mExitTime) > 3000) {
                Toast.makeText(getBaseContext(), "再按一次返回键退出应用", Toast.LENGTH_LONG).show();
                mExitTime = System.currentTimeMillis();
            } else {
                finish();
                System.exit(0);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        refreshFolderData();
    }
}