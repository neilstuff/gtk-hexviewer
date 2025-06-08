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
import org.gnome.gtk.NoSelection;
import org.gnome.gtk.ProgressBar;
import org.gnome.gtk.SignalListItemFactory;
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
        ListStore<Row> store;
        File file;
    
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

    void about() {
       aboutDialog.show();
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
                         columnView.setModel(new NoSelection<Row>(load.store));
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

    public void activate(Application app) {
        GtkBuilder builder = new GtkBuilder();

        try {
            var uiDefinition = GuiUtils.getDefintion("/org/tso/hexviewer.ui");

            builder.addFromString(uiDefinition, uiDefinition.length());

            window = (Window) builder.getObject("main");

            statusBar = (Label) builder.getObject("statusBar");
            progressBar = (ProgressBar) builder.getObject("progressBar");

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