package com.rubenwardy.minetestmodmanager;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.rubenwardy.minetestmodmanager.manager.Mod;
import com.rubenwardy.minetestmodmanager.manager.ModEventReceiver;
import com.rubenwardy.minetestmodmanager.manager.ModList;
import com.rubenwardy.minetestmodmanager.manager.ModManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * An activity representing a list of Mods. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link ModDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
public class ModListActivity
        extends AppCompatActivity
        implements ModEventReceiver {
    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;
    private ModManager mModMan;
    private String install_dir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mod_list);

        mModMan = new ModManager();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getTitle());

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull View view) {
                Resources res = getResources();
                Snackbar.make(view, res.getString(R.string.installing_mod), Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

                Mod mod = new Mod(Mod.ModType.EMT_INVALID, "", "crops", "Crops", "");
                mModMan.installUrlModAsync(getApplicationContext(), mod,
                        "https://github.com/minetest-mods/crops/archive/master.zip", install_dir);
            }
        });

        File extern = Environment.getExternalStorageDirectory();
        if (!extern.exists()) {
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
            alertDialogBuilder.setTitle(R.string.dialog_noext_title);
            alertDialogBuilder.setCancelable(false);
            alertDialogBuilder.setMessage(R.string.dialog_noext_msg);
            alertDialogBuilder.setNegativeButton(R.string.dialog_close, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    ModListActivity.this.finish();
                }
            });
            AlertDialog alertDialog = alertDialogBuilder.create();
            alertDialog.show();
            return;
        }

        // Add Minetest mod location
        File mtroot = new File(extern, "/Minetest");
        File mtdir = new File(mtroot, "/mods");
        File mcroot = new File(extern, "/MultiCraft");
        File mcdir = new File(mcroot, "/mods");
        install_dir = mtdir.getAbsolutePath();

        // Check there is at least one mod dir
        if (mtroot.exists() && !mtdir.exists() && mtdir.mkdirs()) {
            mtdir = new File(mtroot, "/mods");
        }
        if (mcroot.exists() && !mcdir.exists() && mcdir.mkdirs()) {
            mcdir = new File(mcroot, "/mods");
        }
        if (!mtdir.exists() && !mcdir.exists() && !mtdir.mkdirs()) {
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
            alertDialogBuilder.setCancelable(false);
            alertDialogBuilder.setTitle(R.string.dialog_nomt_title);
            alertDialogBuilder.setMessage(R.string.dialog_nomt_msg);
            alertDialogBuilder.setNegativeButton(R.string.dialog_close, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    ModListActivity.this.finish();
                }
            });
            AlertDialog alertDialog = alertDialogBuilder.create();
            alertDialog.show();
            return;
        }

        // Bind event receiver
        mModMan.setEventReceiver(this);

        // Add lists
        Resources res = getResources();
        mModMan.getModsFromDir(res.getString(R.string.minetest_mods), mtroot.getAbsolutePath(), mtdir.getAbsolutePath());
        mModMan.getModsFromDir(res.getString(R.string.multicraft_mods), mcroot.getAbsolutePath(), mcdir.getAbsolutePath());

        View recyclerView = findViewById(R.id.mod_list);
        assert recyclerView != null;
        setupRecyclerView((RecyclerView) recyclerView);

        if (findViewById(R.id.mod_detail_container) != null) {
            mTwoPane = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.w("MLAct", "Resuming!");
        mModMan.setEventReceiver(this);

        for (ModList list : ModManager.lists_map.values()) {
            checkChanges(list);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        ModManager modman = new ModManager();
        modman.unsetEventReceiver(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        ModManager modman = new ModManager();
        modman.unsetEventReceiver(this);
    }

    @Override
    public void onModEvent(@NonNull Bundle bundle) {
        String action = bundle.getString(PARAM_ACTION);
        if (action == null) {
            return;
        } else if (action.equals(ACTION_INSTALL)) {
            String modname = bundle.getString(PARAM_MODNAME);
            Resources res = getResources();
            if (bundle.containsKey(PARAM_ERROR)) {
                String error = bundle.getString(PARAM_ERROR);
                String text = String.format(res.getString(R.string.failed_install), modname, error);
                Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                return;
            } else {
                String text = String.format(res.getString(R.string.installed_mod), modname);
                Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        } else if (mTwoPane && action.equals(ACTION_UNINSTALL)) {
            FragmentManager fragman = getSupportFragmentManager();
            Fragment frag = fragman.findFragmentById(R.id.mod_detail_container);
            getSupportFragmentManager().beginTransaction()
                    .remove(frag)
                    .commit();
        }

        checkChanges(bundle.getString(ModEventReceiver.PARAM_DEST));
    }

    private void checkChanges(@Nullable String listname) {
        if (listname == null || listname.equals("")) {
            return;
        }

        ModList list = mModMan.get(listname);
        checkChanges(list);
    }

    private void checkChanges(@Nullable ModList list) {
        if (list != null && !list.valid && mModMan.update(list)) {
            Log.w("MLAct", " - list has changed!");
            RecyclerView recyclerView = (RecyclerView) findViewById(R.id.mod_list);
            assert recyclerView != null;
            fillRecyclerView(recyclerView);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_mod_list, menu);
        return true;
    }

    private void setupRecyclerView(@NonNull RecyclerView recyclerView) {
        //Add your adapter to the sectionAdapter
        ModListRecyclerViewAdapter adapter = new ModListRecyclerViewAdapter();
        SimpleSectionedRecyclerViewAdapter sectionedAdapter =
                new SimpleSectionedRecyclerViewAdapter(this, R.layout.section, R.id.section_text, adapter);
        recyclerView.setAdapter(sectionedAdapter);

        // Fill view
        fillRecyclerView(recyclerView);
    }

    private void fillRecyclerView(@NonNull RecyclerView recyclerView) {
        Resources res = getResources();
        SimpleSectionedRecyclerViewAdapter adapter =
                (SimpleSectionedRecyclerViewAdapter)recyclerView.getAdapter();

        // Add path lists
        List<Mod> mods = new ArrayList<>();
        List<SimpleSectionedRecyclerViewAdapter.Section> sections =
                new ArrayList<>();
        for (ModList list : ModManager.lists_map.values()) {
            if (list.type == ModList.ModListType.EMLT_PATH) {
                sections.add(new SimpleSectionedRecyclerViewAdapter.Section(mods.size(), list.title));
                mods.addAll(list.mods);
            }
        }
        sections.add(new SimpleSectionedRecyclerViewAdapter.Section(mods.size(),
                res.getString(R.string.mod_store)));

        adapter.setMods(mods);
        // Set sections and update.
        SimpleSectionedRecyclerViewAdapter.Section[] dummy =
                new SimpleSectionedRecyclerViewAdapter.Section[sections.size()];
        adapter.setSections(sections.toArray(dummy));
        adapter.notifyDataSetChanged();
    }

    public class ModListRecyclerViewAdapter
            extends RecyclerView.Adapter<ModListRecyclerViewAdapter.ViewHolder> {

        private List<Mod> mMods;

        public ModListRecyclerViewAdapter() {
            mMods = new ArrayList<>();
        }

        public void setMods(List<Mod> mods) {
            mMods = mods;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.mod_list_content, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
            holder.mItem = mMods.get(position);
            holder.mIdView.setText(holder.mItem.name);
            int len = holder.mItem.desc.indexOf('.') + 1;
            int slen = holder.mItem.desc.length();
            if (len > slen || len < 20) {
                len = slen;
            }
            if (len > 100) {
                len = 100;
            }
            holder.mContentView.setText(holder.mItem.desc.substring(0, len));

            holder.mView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(@NonNull View v) {
                    if (mTwoPane) {
                        Bundle arguments = new Bundle();
                        arguments.putString(ModDetailFragment.ARG_MOD_LIST, holder.mItem.listname);
                        arguments.putString(ModDetailFragment.ARG_MOD_NAME, holder.mItem.name);
                        ModDetailFragment fragment = new ModDetailFragment();
                        fragment.setArguments(arguments);
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.mod_detail_container, fragment)
                                .commit();
                    } else {
                        Context context = v.getContext();
                        Intent intent = new Intent(context, ModDetailActivity.class);
                        intent.putExtra(ModDetailFragment.ARG_MOD_LIST, holder.mItem.listname);
                        intent.putExtra(ModDetailFragment.ARG_MOD_NAME, holder.mItem.name);

                        context.startActivity(intent);
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return mMods.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            @NonNull
            public final View mView;
            @NonNull
            public final TextView mIdView;
            @NonNull
            public final TextView mContentView;
            public Mod mItem;

            public ViewHolder(@NonNull View view) {
                super(view);
                mView = view;
                mIdView = (TextView) view.findViewById(R.id.id);
                mContentView = (TextView) view.findViewById(R.id.content);
            }

            @NonNull
            @Override
            public String toString() {
                return super.toString() + " '" + mContentView.getText() + "'";
            }
        }
    }
}
