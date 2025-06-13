package org.tso;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.gnome.gio.ApplicationFlags;
import org.gnome.gio.File;
import org.gnome.gio.ListStore;
import org.gnome.glib.GLib;
import org.gnome.glib.Type;
import org.gnome.gobject.GObject;
import org.gnome.gtk.AlertDialog;
import org.gnome.gtk.Application;
import org.gnome.gtk.Button;
import org.gnome.gtk.ColumnView;
import org.gnome.gtk.ColumnViewColumn;
import org.gnome.gtk.FileDialog;
import org.gnome.gtk.GtkBuilder;
import org.gnome.gtk.Inscription;
import org.gnome.gtk.Label;
import org.gnome.gtk.ListItem;
import org.gnome.gtk.ProgressBar;
import org.gnome.gtk.SelectionModel;
import org.gnome.gtk.SignalListItemFactory;
import org.gnome.gtk.SingleSelection;
import org.gnome.gtk.TextBuffer;
import org.gnome.gtk.TextIter;
import org.gnome.gtk.TextView;
import org.gnome.gtk.Window;
import org.tso.util.GuiUtils;

import io.github.jwharm.javagi.base.GErrorException;
import io.github.jwharm.javagi.base.Out;
import io.github.jwharm.javagi.gobject.types.Types;

public class HexViewer {

    private static final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();

    Window window;
    ColumnView columnView;
    AboutDialog aboutDialog;
    Label statusBar;
    ProgressBar progressBar;
    TextView rowView;

    Load load = null;

    public static final class Row extends GObject {

        public static Type gtype = Types.register(Row.class);
        public String address;
        public String[] hexValues;
        public String asciiValues;

        public Row(int line, String[] hexValues, String asciiValues) {

            this.address = String.format("%1$010X", line);
            this.hexValues = hexValues;
            this.asciiValues = asciiValues;

        }

        public String getValue(int iHex) {
            return this.hexValues[iHex];
        }

    }

    class Load implements Runnable {

        File file;
        ListStore<Row> store;

        Load(File file) {

            this.file = file;
            this.store = new ListStore<>(Row.gtype);

        }

        void createRows(ListStore<Row> store, byte[] bytes) throws Exception {
            var row = new byte[16];

            this.store = new ListStore<>(Row.gtype);
            ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
            int read;
            int line = 0;

            for (stream.read(row); (read = stream.available()) > 0; stream.read(row)) {
                ArrayList<Object> values = GuiUtils.asHex(row, read < row.length ? read : row.length);
                String[] hexValues = (String[]) (values.get(0));
                String asciiValues = (values.get(1)).toString();

                this.store.append(new Row(line, hexValues, asciiValues));

                line += row.length;

            }

        }

        @Override
        public void run() {
            try {

                Out<byte[]> contents = new Out<>();
                this.file.loadContents(null, contents, null);

                byte[] bytes = contents.get();

                createRows(this.store, bytes);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    void setupColumns(ColumnView columnview) {
        var columnFactory = new SignalListItemFactory();

        columnFactory.onSetup(item -> {
            var listitem = (ListItem) item;
            var inscription = Inscription.builder()
                    .setXalign(0)
                    .build();
            listitem.setChild(inscription);

        });

        columnFactory.onBind(item -> {
            var listitem = (ListItem) item;
            var inscription = (Inscription) listitem.getChild();

            if (inscription != null) {
                var row = (Row) listitem.getItem();
                inscription.setText(row.address);
            }
        });

        var column = new ColumnViewColumn("", columnFactory);

        column.setFixedWidth(100);
        columnview.appendColumn(column);

        for (int iColumn = 0; iColumn < HEX_CHARS.length; iColumn++) {
            columnFactory = new SignalListItemFactory();

            columnFactory.onSetup(item -> {
                var listitem = (ListItem) item;
                var inscription = Inscription.builder()
                        .setXalign(0)
                        .build();
                listitem.setChild(inscription);

            });

            final int hexColumn = iColumn;

            columnFactory.onBind(item -> {
                var listitem = (ListItem) item;
                var inscription = (Inscription) listitem.getChild();
                var row = (Row) listitem.getItem();
                if (inscription != null) {
                    inscription.setText(row.getValue(hexColumn));
                }
            });

            column = new ColumnViewColumn(HEX_CHARS[iColumn] + "", columnFactory);

            columnview.appendColumn(column);

        }

        columnFactory = new SignalListItemFactory();

        columnFactory.onSetup(item -> {
            var listitem = (ListItem) item;
            var inscription = Inscription.builder()
                    .setXalign(0)
                    .build();
            listitem.setChild(inscription);

        });

        columnFactory.onBind(item -> {
            var listitem = (ListItem) item;
            var inscription = (Inscription) listitem.getChild();

            if (inscription != null) {
                var row = (Row) listitem.getItem();
                inscription.setText(row.asciiValues);
            }
        });

        column = new ColumnViewColumn("", columnFactory);

        column.setExpand(true);

        columnview.appendColumn(column);


    }

    void about() {
        aboutDialog.show();
    }

    void open() {
        FileDialog dialog = new FileDialog();

        dialog.open(this.window, null, (_, result, _) -> {
            File file = null;

            try {
                file = dialog.openFinish(result);
            } catch (GErrorException ignored) {
            }
            if (file == null) {
                return;
            }

            // Load the contents of the selected file.
            try {
                Path path = Paths.get(file.getPath());

                statusBar.setText(path.getFileName().toString());

                load = new Load(file);

                Thread loader = new Thread(load);
                loader.start();

                progressBar.setVisible(true);
                progressBar.setFraction(0);

                GLib.timeoutAdd(GLib.PRIORITY_DEFAULT, 100, () -> {

                    if (!loader.isAlive()) {
                        columnView.setModel(new SingleSelection<Row>(load.store));
                        showRow(load.store, 0);

                        ((SingleSelection<?>) (columnView.getModel())).onSelectionChanged(new SelectionModel.SelectionChangedCallback() {
                            @Override
                            public void run(int position, int nItems) {
                              showRow(load.store, ((SingleSelection<?>) (columnView.getModel())).getSelected());
                            }

                        });

                        progressBar.setVisible(false);
                    } else {
                        progressBar.pulse();
                    }

                    return loader.isAlive();

                });

            } catch (Exception e) {
                AlertDialog.builder()
                        .setModal(true)
                        .setMessage("Error reading from file")
                        .setDetail(e.getMessage())
                        .build()
                        .show(this.window);
            }

            window.setCursorFromName("default");

        });
    }

    String hexToOctal(String hex) {
        int decimalValue = Integer.parseInt(hex, 16);
        var octalValue =  Integer.toOctalString(decimalValue);

        return String.format("%3s", octalValue).replace(' ', '0');

    }
        
    void showRow(ListStore<Row> store, int index) {

        rowView.setMonospace(true);
        TextBuffer buffer = new TextBuffer();
        TextIter iter = new TextIter();
        buffer.getStartIter(iter);

        Row row = store.get(index);

        String definition = "<span weight=\"heavy\" size=\"medium\">Address: </span>" + row.address + "\n\n";
        String hexValues  = "    Octal |  ";
        String asciiValues = "    Hex   |   ";
        String octalValues = "    Ascii | ";

        for (int iHex = 0; iHex < row.hexValues.length; iHex++) {
   
            if (iHex < row.asciiValues.length()) {
                hexValues += row.hexValues[iHex] + " |  ";
                octalValues += hexToOctal(row.hexValues[iHex]) + " | ";

                asciiValues += row.asciiValues.substring(iHex, iHex + 1) + " |   ";
            } else {
                asciiValues += "  |   ";
                hexValues += "   |  ";
                octalValues += "    | ";
            }
        }
        
        definition += octalValues  + "\n";
        definition += hexValues + "\n";
        definition += asciiValues;
        
        buffer.insertMarkup(iter, definition, -1);
        rowView.setBuffer(buffer);
    }
    
    public void activate(Application app) {
        GtkBuilder builder = new GtkBuilder();

        try {
            var uiDefinition = GuiUtils.getDefintion("/org/tso/hexviewer.ui");

            builder.addFromString(uiDefinition, uiDefinition.length());

            window = (Window) builder.getObject("main");

            statusBar = (Label) builder.getObject("statusBar");
            progressBar = (ProgressBar) builder.getObject("progressBar");
            rowView = (TextView) builder.getObject("rowView");

            var openToolbarButton = (Button) builder.getObject("openToolbarButton");
            var aboutToolbarItem = (Button) builder.getObject("aboutToolbarItem");

            openToolbarButton.onClicked(this::open);
            aboutToolbarItem.onClicked(this::about);

            columnView = (ColumnView) builder.getObject("hexViewer");
            columnView.addCssClass("monospace");

            columnView.setShowColumnSeparators(true);

            setupColumns(columnView);

            aboutDialog = new AboutDialog(window, "/org/tso/about-dialog.ui");

            window.setApplication(app);

            window.setVisible(true);

        } catch (Exception e) {

            e.printStackTrace();

        }

    }

    public HexViewer(String[] args) {
        Application app = new Application("org.tso.HexViewer", ApplicationFlags.DEFAULT_FLAGS);

        app.onActivate(() -> activate(app));
        app.run(args);
    }

    public static void main(String[] args) {
        new HexViewer(args);
    }
}
