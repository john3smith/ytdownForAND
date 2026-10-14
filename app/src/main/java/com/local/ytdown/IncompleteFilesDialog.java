package com.local.ytdown;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;

/** File IO is off the UI thread and shares the downloader lock, including after cancellation. */
final class IncompleteFilesDialog {
    private final Activity activity;
    private final ExecutorService executor;
    private final BooleanSupplier downloading;
    private final Handler main=new Handler(Looper.getMainLooper());
    private AlertDialog dialog,confirmation;
    private TextView summary;
    private ListView list;
    private List<IncompleteFileStore.Entry> entries=Collections.emptyList();
    private boolean loading;
    IncompleteFilesDialog(Activity a,ExecutorService executor,BooleanSupplier downloading) {
        activity=a;this.executor=executor;this.downloading=downloading;
    }
    private boolean ready() { return !activity.isFinishing() && !activity.isDestroyed() && dialog!=null && dialog.isShowing(); }
    void dismiss() { if(confirmation!=null) confirmation.dismiss(); if(dialog!=null) dialog.dismiss(); }
    private boolean idle() {
        if(downloading.getAsBoolean()) {
            Toast.makeText(activity,"다운로드·계정 검색·대기열을 종료한 뒤 이용해 주세요.",Toast.LENGTH_LONG).show();return false;
        }
        return true;
    }
    void show() {
        if(dialog!=null && dialog.isShowing()) return;
        if(loading) { Toast.makeText(activity,"파일 처리가 끝난 뒤 다시 열어 주세요.",Toast.LENGTH_SHORT).show();return; }
        if(!idle()) return;
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
        int p=(int)(16*activity.getResources().getDisplayMetrics().density);body.setPadding(p,p,p,p);
        summary=new TextView(activity);body.addView(summary);
        list=new ListView(activity);body.addView(list,new LinearLayout.LayoutParams(-1,(int)(300*activity.getResources().getDisplayMetrics().density)));
        list.setOnItemClickListener((parent,view,pos,id)->{ if(!loading && pos<entries.size()) confirm(Collections.singletonList(entries.get(pos))); });
        dialog=new AlertDialog.Builder(activity).setTitle("미완료 파일")
                .setView(body).setNegativeButton("닫기",null).setNeutralButton("새로고침",null)
                .setPositiveButton("전체 삭제",null).create();
        dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{if(idle()) refresh();});
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->confirm(new ArrayList<>(entries)));
            refresh();
        });
        dialog.show();
    }
    private void busy(String message) {
        loading=true;summary.setText(message);list.setEnabled(false);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
    }
    private void render(IncompleteFileStore.Listing result,String prefix) {
        loading=false;
        if(!ready()) return;
        entries=result.entries;long total=0;List<String> rows=new ArrayList<>();
        for(IncompleteFileStore.Entry e:entries) {
            total+=e.size;rows.add(e.name+"\n"+Formatter.formatFileSize(activity,e.size)+" · "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(e.modified))+"\n"+e.location+" · 눌러서 삭제");
        }
        summary.setText(prefix+"미완료 "+entries.size()+"개 · "+Formatter.formatFileSize(activity,total)
                +"\n.part·조각·재개 정보와 앱의 저장 미완료 항목만 표시합니다. 완료 영상/사진 및 판별이 불확실한 파일은 제외합니다."
                +(result.unavailable>0?"\n일부 저장소에 접근하지 못했습니다. 전체가 조회된 것은 아닙니다.":""));
        list.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_list_item_1,rows));list.setEnabled(true);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!entries.isEmpty());
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
    }
    private void refresh() {
        if(loading) return;
        busy("미완료 파일을 확인하고 있습니다…");
        executor.execute(()->{
            IncompleteFileStore.Listing result;
            synchronized(IncompleteFiles.IO_LOCK) { result=IncompleteFileStore.scan(activity.getApplicationContext()); }
            main.post(()->render(result,""));
        });
    }
    private void confirm(List<IncompleteFileStore.Entry> selected) {
        if(loading || selected.isEmpty() || !idle() || (confirmation!=null && confirmation.isShowing())) return;
        long total=0;for(IncompleteFileStore.Entry e:selected) total+=e.size;
        confirmation=new AlertDialog.Builder(activity).setTitle(selected.size()==1?"미완료 파일 삭제":"미완료 파일 전체 삭제")
                .setMessage((selected.size()==1?selected.get(0).name+"\n":"현재 목록의 "+selected.size()+"개 파일\n")
                        +Formatter.formatFileSize(activity,total)+"를 영구 삭제합니다. 휴지통으로 이동하지 않으며 해당 파일은 이어받을 수 없습니다.\n완료 파일은 삭제하지 않습니다.")
                .setNegativeButton("취소",null).setPositiveButton("삭제",(d,w)->remove(selected)).show();
    }
    private void remove(List<IncompleteFileStore.Entry> selected) {
        if(!idle() || !ready()) return;
        busy("미완료 파일을 삭제하고 있습니다…");
        executor.execute(()->{
            int removed=0;IncompleteFileStore.Listing result;
            synchronized(IncompleteFiles.IO_LOCK) {
                for(IncompleteFileStore.Entry e:selected) if(IncompleteFileStore.delete(activity.getApplicationContext(),e)) removed++;
                result=IncompleteFileStore.scan(activity.getApplicationContext());
            }
            String message=removed+"개 삭제 · "+(selected.size()-removed)+"개 변경/접근 불가로 건너뜀\n";
            main.post(()->render(result,message));
        });
    }
}
