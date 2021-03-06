package com.github.isabsent.filepicker;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.v4.util.Pair;
import android.support.v7.app.AlertDialog;
import android.text.Spannable;
import android.text.SpannableString;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.github.isabsent.filepicker.comparator.FileNameComparator;
import com.github.isabsent.filepicker.entity.Item;
import com.github.isabsent.filepicker.entity.ItemViewHolder;
import com.github.isabsent.filepicker.entity.SimpleFilePickerItem;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import eltos.simpledialogfragment.SimpleDialog;
import eltos.simpledialogfragment.color.SimpleColorDialog;
import eltos.simpledialogfragment.list.AdvancedAdapter;
import eltos.simpledialogfragment.list.CustomListDialog;

import static com.github.isabsent.filepicker.SimpleFilePickerDialog.CompositeMode.FILE_OR_FOLDER_DIRECT_CHOICE_SELECTION;
import static com.github.isabsent.filepicker.SimpleFilePickerDialog.ItemMode.ITEM_FILE_FOLDER;
import static com.github.isabsent.filepicker.SimpleFilePickerDialog.ItemMode.ITEM_FILE_ONLY;
import static com.github.isabsent.filepicker.SimpleFilePickerDialog.ItemMode.ITEM_FOLDER_ONLY;
import static eltos.simpledialogfragment.SimpleDialog.OnDialogResultListener.BUTTON_NEGATIVE;
import static eltos.simpledialogfragment.SimpleDialog.OnDialogResultListener.BUTTON_NEUTRAL;
import static eltos.simpledialogfragment.SimpleDialog.OnDialogResultListener.BUTTON_POSITIVE;

public class SimpleFilePickerDialog extends CustomListDialog<SimpleFilePickerDialog> {
    private static final String
            TAG = "simpleListDialog",
            COMPOSITE_MODE = TAG + "compositeMode",
            PATH_ARRAY = TAG + "pathArray",
            FOLDER_PATH = TAG + "folderPath",
            BUTTONS_ENABLED = TAG + "buttonsEnabled";

    protected final static String DATA_SET = TAG + "data_set";

    /**
     * Key for an <b>ArrayList&lt;String&gt;</b> returned by {@link SimpleFilePickerDialog#onResult}
     */
    public static final String
            SELECTED_LABELS = TAG + "selectedLabels",
            SELECTED_PATHS = TAG + "selectedPaths";

    /**
     * Key for a <b>String</b> returned by {@link SimpleFilePickerDialog#onResult} in single choice mode
     */
    public static final String
            SELECTED_SINGLE_LABEL = TAG + "selectedSingleLabel",
            SELECTED_SINGLE_PATH = TAG + "selectedSinglePath",
            HIGHLIGHT = TAG + "highlight";

    private ArrayList<SimpleFilePickerItem> mData;
    private Button openButton, upButton, selectButton;
    private CompositeMode mode;
    private int choiceMode;
    private String folderPath;
    private InteractionListenerString mListenerString;
    private InteractionListenerInt mListenerInt;

    public static SimpleFilePickerDialog build(String folderPath, CompositeMode mode){
        if (folderPath == null)
            folderPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (mode == null)
            mode = FILE_OR_FOLDER_DIRECT_CHOICE_SELECTION;

        return new SimpleFilePickerDialog()
                .path(folderPath, mode)
                .choiceMin(1)
                .neut(R.string.button_up)
                .neg(R.string.button_open)
                .pos(R.string.button_select);
    }

    /**
     * If set to true, show an input field at the to of the list and allow the user
     * to filter the list
     *
     * @param enabled weather to allow filtering or not
     * @param highlight weather to highlight the text filtered
     */
    public SimpleFilePickerDialog filterable(boolean enabled, boolean highlight) {
        setArg(HIGHLIGHT, highlight);
        return super.filterable(enabled);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            folderPath = getArguments().getString(FOLDER_PATH);
            mode = CompositeMode.values()[getArguments().getInt(COMPOSITE_MODE)];
            choiceMode = getArguments().getInt(CHOICE_MODE);
        }
    }

    @Override
    protected SimpleFilePickerAdapter onCreateAdapter() {
        int layout;

        switch (choiceMode) {
            case SINGLE_CHOICE:
                layout = R.layout.simple_list_item_single_choice;
                break;
            case MULTI_CHOICE:
                layout = R.layout.simple_list_item_multiple_choice;
                break;
            case NO_CHOICE:
            case SINGLE_CHOICE_DIRECT:
            default:
                layout = R.layout.simple_list_item_1;
                break;
        }

        if (getArguments() != null) {
            mData = getArguments().getParcelableArrayList(DATA_SET);
            if (mData == null)
                mData = new ArrayList<>(0);
        }
        return new SimpleFilePickerAdapter(layout, mData, this);
    }

    public void onActivityCreated(Bundle savedInstanceState) { //Restore the fragment's state here
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            folderPath = savedInstanceState.getString(FOLDER_PATH);
            mode = CompositeMode.values()[savedInstanceState.getInt(COMPOSITE_MODE)];
            buttonsEnabled = savedInstanceState.getBooleanArray(BUTTONS_ENABLED);
        }
    }

    private boolean[] buttonsEnabled;

    @Override
    public void onSaveInstanceState(Bundle outState) { //Save the fragment's state here
        super.onSaveInstanceState(outState);
        outState.putString(FOLDER_PATH, folderPath);
        outState.putInt(COMPOSITE_MODE, mode.ordinal());
        buttonsEnabled = new boolean[]{upButton.isEnabled(), openButton.isEnabled(), selectButton.isEnabled()};
        outState.putBooleanArray(BUTTONS_ENABLED, buttonsEnabled);
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog alertDialog = (AlertDialog) getDialog();
        if (alertDialog != null) {
            upButton = alertDialog.getButton(Dialog.BUTTON_NEUTRAL);//Up
            if (isExternalStorageRoot(folderPath))
                upButton.setEnabled(false);

            openButton = alertDialog.getButton(Dialog.BUTTON_NEGATIVE);//Open
            openButton.setEnabled(false);

            selectButton = alertDialog.getButton(Dialog.BUTTON_POSITIVE);//Select
            if (ITEM_FILE_ONLY.equals(mode.getItemMode()))
                selectButton.setEnabled(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (buttonsEnabled != null) {
            upButton.setEnabled(buttonsEnabled[0]);
            openButton.setEnabled(buttonsEnabled[1]);
            selectButton.setEnabled(buttonsEnabled[2]);
        }
    }

    @Override
    protected Bundle onResult(int which) {
        Bundle result = super.onResult(which);
        if (result != null) {
            switch (which) {
                case BUTTON_NEUTRAL://Up
                    showListItemDialog(new File(folderPath).getParent());
                    return result;
                case BUTTON_NEGATIVE://Open
                    String selectedPath = getPathToOpen(result);
                    if (selectedPath != null) {
                        showListItemDialog(selectedPath);
                        return result;
                    }
                    break;
                case BUTTON_POSITIVE: //Select
                    if (isSelectionEmpty(result)) {//Choosing a parent folder
                        String[] paths = getArguments().getStringArray(PATH_ARRAY);
                        if (paths != null) {
                            result.putString(SELECTED_SINGLE_LABEL, FilenameUtils.getName(folderPath));
                            result.putString(SELECTED_SINGLE_PATH, folderPath);
                        }
                        return result;
                    }
                    break;
            }

            ArrayList<Integer> positions = result.getIntegerArrayList(SELECTED_POSITIONS);

            if (positions != null && positions.isEmpty() && !result.containsKey(SELECTED_SINGLE_POSITION) && which == BUTTON_NEGATIVE) {//Select
                result.putString(SELECTED_SINGLE_PATH, folderPath);
                return result;
            }

            if (positions != null && !positions.isEmpty() && getArguments() != null) {
                String[] paths = getArguments().getStringArray(PATH_ARRAY);
                if (paths != null) {
                    ArrayList<String> labels = new ArrayList<>(positions.size());
                    ArrayList<String> selectedPaths = new ArrayList<>(positions.size());
                    for (Integer pos : positions) {
                        labels.add(mData.get(pos).toString());
                        selectedPaths.add(paths[pos]);
                    }
                    result.putStringArrayList(SELECTED_LABELS, labels);
                    result.putStringArrayList(SELECTED_PATHS, selectedPaths);
                }
            }

            if (result.containsKey(SELECTED_SINGLE_POSITION) && getArguments() != null) {
                String[] paths = getArguments().getStringArray(PATH_ARRAY);
                if (paths != null) {
                    int selectedPosition = result.getInt(SELECTED_SINGLE_POSITION);
                    SimpleFilePickerItem simpleFilePickerItem = mData.get(selectedPosition);
                    String selectedLabel = simpleFilePickerItem.toString();
                    result.putString(SELECTED_SINGLE_LABEL, selectedLabel);
                    result.putString(SELECTED_SINGLE_PATH, paths[selectedPosition]);
                }
            }
        }
        return result;
    }

    private boolean isSelectionEmpty(Bundle result) {
        return !result.containsKey(SELECTED_SINGLE_POSITION) && (!result.containsKey(SELECTED_POSITIONS) || result.getIntegerArrayList(SELECTED_POSITIONS).isEmpty());
    }

    private String getPathToOpen(Bundle extras){
        if (getArguments() != null) {
            String[] paths = getArguments().getStringArray(PATH_ARRAY);
            if (paths != null) {
                int selectedPathPosition = extras.getInt(SimpleColorDialog.SELECTED_SINGLE_POSITION, -1);
                if (selectedPathPosition < 0) {
                    List<Integer> selectedPathPositions = extras.getIntegerArrayList(SimpleColorDialog.SELECTED_POSITIONS);
                    if (selectedPathPositions != null && !selectedPathPositions.isEmpty()) {
                        if (selectedPathPositions.size() == 1)
                            selectedPathPosition = selectedPathPositions.iterator().next();
                        else {
                            for (Integer position : selectedPathPositions)
                                if (!new File(paths[position]).isFile()) {
                                    if (selectedPathPosition >= 0) {
                                        selectedPathPosition = -1;
                                        break;
                                    } else
                                        selectedPathPosition = position;
                                }
                        }
                    }
                }
                if (selectedPathPosition >= 0 && !new File(paths[selectedPathPosition]).isFile())
                    return paths[selectedPathPosition];
            }
        }
        return null;
    }

    private void showListItemDialog(String path){
        int titleResId = 0;
        String title = null;
        if (getArguments() != null) {
            Object value = getArguments().get(SimpleDialog.TITLE);
            if (value instanceof String)
                title = (String) value;
            else if (value instanceof Integer)
                titleResId = (Integer) value;

            if (isPathAcceptable(path)) {
                if (title != null && mListenerString != null)
                    mListenerString.showListItemDialog(title, path, mode, getTag());
                else if (titleResId > 0 && mListenerInt != null )
                    mListenerInt.showListItemDialog(titleResId, path, mode, getTag());
                else if (title == null && titleResId == 0)
                    mListenerString.showListItemDialog(null, path, mode, getTag());
            }
        }
    }

    private static boolean isPathAcceptable(String path){
        String rootExternalStoragePath = Environment.getExternalStorageDirectory().getAbsolutePath();
        return path.startsWith(rootExternalStoragePath);
    }

    public SimpleFilePickerDialog path(String folderPath, final CompositeMode mode) {
        choiceMode(mode.getChoiceMode());
        setArg(COMPOSITE_MODE, mode.ordinal());
        setArg(FOLDER_PATH, folderPath);
        if (getArguments() != null) {
            final ItemMode itemMode = mode.getItemMode();
            File[] items = new File(folderPath).listFiles(new FileFilter() {

                @Override
                public boolean accept(File file) {
                    return ITEM_FILE_FOLDER.equals(itemMode) || ITEM_FILE_ONLY.equals(itemMode) || file.isDirectory();
                }
            });
//            if (items.length == 0)
                 emptyText("List is empty!");
//            else {
                List<File> itemsList = Arrays.asList(items);
                Collections.sort(itemsList, new FileNameComparator(true));

                ArrayList<SimpleFilePickerItem> list = new ArrayList<>(items.length);
                int i = 0;
                String[] itemPaths = new String[items.length];
                String[] itemNames = new String[items.length];
                boolean[] isFiles = new boolean[items.length];
                for (File file : itemsList) {
                    itemPaths[i] = file.getAbsolutePath();
                    itemNames[i] = file.getName();
                    isFiles[i] = file.isFile();
                    list.add(new SimpleFilePickerItem(new Item(itemNames[i], isFiles[i]), itemNames[i].hashCode()));
                    i++;
                }
                getArguments().putParcelableArrayList(DATA_SET, list);
                getArguments().putStringArray(PATH_ARRAY, itemPaths);
//            }
        }
        return this;
    }

    public static class SimpleFilePickerAdapter extends AdvancedAdapter<Item> {
        private SimpleFilePickerDialog mDialog;
        private int mLayout;
        private int choiceMode;
        private ItemMode itemMode;
        private CompositeMode mode;

        public SimpleFilePickerAdapter(@LayoutRes int layout, ArrayList<SimpleFilePickerItem> data, SimpleFilePickerDialog dialog){
            mLayout = layout;
            mDialog = dialog;
            choiceMode = mDialog.getArguments().getInt(CHOICE_MODE);
            mode = CompositeMode.values()[mDialog.getArguments().getInt(COMPOSITE_MODE)];
            itemMode = mode.getItemMode();

            ArrayList<Pair<Item, Long>> dataAndIds = new ArrayList<>(data.size());
            for (SimpleFilePickerItem simpleFilePickerItem : data)
                dataAndIds.add(new Pair<>(simpleFilePickerItem.getItem(), simpleFilePickerItem.getId()));

            setDataAndIds(dataAndIds);
        }

        private AdvancedFilter mFilter = new AdvancedFilter(true, true){

            @Override
            protected boolean matches(Item object, @NonNull CharSequence constraint) {
                return matches(object.toString());
            }
        };

        @Override
        public AdvancedFilter getFilter() {
            return mFilter;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ItemViewHolder viewHolder;
            if (convertView == null) {
                convertView = LayoutInflater.from(mDialog.getContext()).inflate(mLayout, parent, false);
                viewHolder = new ItemViewHolder(convertView);
                convertView.setTag(viewHolder);
            } else
                viewHolder = (ItemViewHolder) convertView.getTag();

            Item item = getItem(position);
            boolean isItemChecked = isItemChecked(position);
            Spannable text;
            if (mDialog.getArguments().getBoolean(HIGHLIGHT))
                text = highlight(item.toString(), mDialog.getContext());
            else
                text = new SpannableString(item.toString());
            viewHolder.bind(item, mode, isItemChecked, text);
            convertView.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View view) {
                    toggleChecked(position);
                    notifyDataSetChanged();

                    List<Item> checkedItems = getCheckedItems();
                    List<Item> checkedFolders = null;
                    List<Item> checkedFiles = null;
                    if (!checkedItems.isEmpty()) {
                        checkedFolders = new ArrayList<>();
                        checkedFiles = new ArrayList<>();
                        for (Item checkedItem : checkedItems){
                            if (checkedItem.isFile())
                                checkedFiles.add(checkedItem);
                            else
                                checkedFolders.add(checkedItem);
                        }
                    }

                    boolean isDirectChoiceMode = choiceMode == SINGLE_CHOICE_DIRECT;
                    boolean isSelectEnabled = false;
                    if (!isDirectChoiceMode) {
                        boolean areItemsChecked = checkedItems.size() > 0;
                        if (ITEM_FILE_ONLY.equals(itemMode)) {
                            boolean areFoldersChecked = checkedFolders != null && checkedFolders.size() > 0;
                            isSelectEnabled = !areFoldersChecked && areItemsChecked;
                        } else {
                            isSelectEnabled = areItemsChecked;
                        }
                    }

                    boolean isSingleFolderChecked = checkedFolders != null && checkedFolders.size() == 1;
                    if (isDirectChoiceMode) {
                        boolean isSingleItemChecked = checkedItems.size() == 1;
                        if (CompositeMode.isImmediate(mode)) {
                            if (isSingleFolderChecked)
                                mDialog.pressNegativeButton();//Open
                            else if (isSingleItemChecked)
                                mDialog.pressPositiveButton();//Select
                        } else {
                            if (ITEM_FILE_ONLY.equals(itemMode))
                                isSelectEnabled = isSingleItemChecked && !isSingleFolderChecked;
                            else
                                isSelectEnabled = isSingleItemChecked;
                            mDialog.setButtons(isSingleFolderChecked, isSelectEnabled);
                        }
                    } else {
                        mDialog.setButtons(isSingleFolderChecked, isSelectEnabled);
                    }
                }
            });
            return super.getView(position, convertView, parent);
        }
    }

    protected final void pressNegativeButton(){
//        if (acceptsNegativeButtonPress()) {
            getDialog().dismiss();
            callResultListener(DialogInterface.BUTTON_NEGATIVE, null);
//        }
    }

    private void setButtons(boolean isOpenEnabled, boolean isSelectEnabled) {
        openButton.setEnabled(isOpenEnabled);
        selectButton.setEnabled(isSelectEnabled);
    }

    private static boolean isExternalStorageRoot(String path) {
        String rootExternalStoragePath = Environment.getExternalStorageDirectory().getAbsolutePath();
        return rootExternalStoragePath.equals(path);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof InteractionListenerString)
            mListenerString = (InteractionListenerString) context;
        if (context instanceof InteractionListenerInt)
            mListenerInt = (InteractionListenerInt) context;
        if (mListenerString == null && mListenerInt == null)
            throw new RuntimeException(context.toString() + " must implement InteractionListenerString or InteractionListenerInt");

    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListenerInt = null;
        mListenerString = null;
    }

    @Override
    protected void onDialogShown() {
        super.onDialogShown();
        setPositiveButtonEnabled(!ItemMode.ITEM_FILE_ONLY.equals(mode.getItemMode()));
    }

    public enum CompositeMode {
        FILE_ONLY_SINGLE_CHOICE(ITEM_FILE_ONLY, SINGLE_CHOICE),
        FILE_ONLY_MULTI_CHOICE(ITEM_FILE_ONLY, MULTI_CHOICE),
        FILE_ONLY_DIRECT_CHOICE_IMMEDIATE(ITEM_FILE_ONLY, SINGLE_CHOICE_DIRECT),
        FILE_ONLY_DIRECT_CHOICE_SELECTION(ITEM_FILE_ONLY, SINGLE_CHOICE_DIRECT),

        FOLDER_ONLY_SINGLE_CHOICE(ITEM_FOLDER_ONLY, SINGLE_CHOICE),
        FOLDER_ONLY_MULTI_CHOICE(ITEM_FOLDER_ONLY, MULTI_CHOICE),
        FOLDER_ONLY_DIRECT_CHOICE_IMMEDIATE(ITEM_FOLDER_ONLY, SINGLE_CHOICE_DIRECT),
        FOLDER_ONLY_DIRECT_CHOICE_SELECTION(ITEM_FOLDER_ONLY, SINGLE_CHOICE_DIRECT),

        FILE_OR_FOLDER_SINGLE_CHOICE(ITEM_FILE_FOLDER, SINGLE_CHOICE),
        FILE_AND_FOLDER_MULTI_CHOICE(ITEM_FILE_FOLDER, MULTI_CHOICE),
        FILE_OR_FOLDER_DIRECT_CHOICE_IMMEDIATE(ITEM_FILE_FOLDER, SINGLE_CHOICE_DIRECT),
        FILE_OR_FOLDER_DIRECT_CHOICE_SELECTION(ITEM_FILE_FOLDER, SINGLE_CHOICE_DIRECT);

        private ItemMode itemMode;
        private int choiceMode;

        CompositeMode(ItemMode itemMode, int choiceMode) {
            this.itemMode = itemMode;
            this.choiceMode = choiceMode;
        }

        public ItemMode getItemMode() {
            return itemMode;
        }

        public int getChoiceMode() {
            return choiceMode;
        }

        public static boolean isImmediate(CompositeMode mode){
            return FILE_ONLY_DIRECT_CHOICE_IMMEDIATE.equals(mode)
                    || FOLDER_ONLY_DIRECT_CHOICE_IMMEDIATE.equals(mode)
                    || FILE_OR_FOLDER_DIRECT_CHOICE_IMMEDIATE.equals(mode);
        }
    }

    enum ItemMode{
        ITEM_FILE_ONLY, ITEM_FOLDER_ONLY, ITEM_FILE_FOLDER
    }

    public interface InteractionListenerString extends OnDialogResultListener {
        void showListItemDialog(String title, String folderPath, SimpleFilePickerDialog.CompositeMode mode, String dialogTag);
    }

    public interface InteractionListenerInt extends OnDialogResultListener {
        void showListItemDialog(int titleResId, String folderPath, SimpleFilePickerDialog.CompositeMode mode, String dialogTag);
    }
}
