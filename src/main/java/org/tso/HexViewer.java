package org.tso;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

import org.gnome.gio.ApplicationFlags;
import org.gnome.gio.File;
import org.gnome.gio.ListStore;
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
import org.gnome.gtk.ListItem;
import org.gnome.gtk.NoSelection;
import org.gnome.gtk.SignalListItemFactory;
import org.gnome.gtk.SingleSelection;
import org.gnome.gtk.Window;

import io.github.jwharm.javagi.base.GErrorException;
import io.github.jwharm.javagi.base.Out;
import io.github.jwharm.javagi.gobject.types.Types;

public class HexViewer {

    private static final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();

    Window window;
    private File file = null;
    ListStore<Row> store;

    public static final class Row extends GObject {

        public static Type gtype = Types.register(Row.class);
        public String[] hexValues;
        public String asciiValues;

        public Row(String[] hexValues, String asciiValues) {

            this.hexValues = hexValues;
            this.asciiValues = asciiValues;

        }

        public String getValue(int iHex) {
            return this.hexValues[iHex];
        }
    }

    ArrayList<Object> asHex(byte[] buf) {

        var values = new ArrayList<Object>(2);

        StringBuffer asciiChars = new StringBuffer();

        String[] hex = new String[buf.length];

        for (int iHex = 0; iHex < buf.length; iHex++) {
            hex[iHex] = "";
        }

        for (int i = 0, c = 0; i < buf.length; i++, c++) {
            char[] chars = new char[2];

            chars[0] = HEX_CHARS[(buf[i] & 0xF0) >>> 4];
            chars[1] = HEX_CHARS[buf[i] & 0x0F];

            hex[c] = new String(chars);

            asciiChars.append(Character.isLetterOrDigit(buf[i]) ? (char) buf[i] : '.');

        }

        values.add(hex);
        values.add(asciiChars);

        return values;
    }

    void setupColumns(ColumnView columnview) {

        for (int iColumn = 0; iColumn < HEX_CHARS.length; iColumn++) {
            var columnFactory = new SignalListItemFactory();

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
                inscription.setText(row.getValue(hexColumn));
            });

            var column = new ColumnViewColumn(HEX_CHARS[iColumn] + "", columnFactory);

            columnview.appendColumn(column);

        }

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
            var row = (Row) listitem.getItem();
            inscription.setText(row.asciiValues);
        });

        var column = new ColumnViewColumn("", columnFactory);
        column.setExpand(true);

        columnview.appendColumn(column);

    }

    void createRows(ListStore<Row> store, byte[] bytes) throws Exception {
        var row = new byte[16];

        ByteArrayInputStream stream = new ByteArrayInputStream(bytes);

        for (stream.read(row); stream.available() > 0; stream.read(row)) {
            ArrayList<Object> values = asHex(row);
            String[] hexValues = (String[]) (values.get(0));
            String asciiValues = (values.get(1)).toString();

            store.append(new Row(hexValues, asciiValues));
        }

    }

    void open() {
        FileDialog dialog = new FileDialog();

        dialog.open(this.window, null, (_, result, _) -> {
            try {
                file = dialog.openFinish(result);
            } catch (GErrorException ignored) {
            } // used clicked cancel
            if (file == null) {
                return;
            }

            // Load the contents of the selected file.
            try {

                System.out.println("Removing all rows");

                store.removeAll();

                // The byte[] parameter is an out-parameter in the C API.
                // Create an empty Out<byte[]> object, and read its value afterward.
                Out<byte[]> contents = new Out<>();
                file.loadContents(null, contents, null);

                byte[] bytes = contents.get();

                createRows(store, bytes);

            } catch (Exception e) {
                AlertDialog.builder()
                        .setModal(true)
                        .setMessage("Error reading from file")
                        .setDetail(e.getMessage())
                        .build()
                        .show(this.window);
            }
        });
    }

    public void activate(Application app) {
        GtkBuilder builder = new GtkBuilder();

        try {
            InputStream inputStream = HexViewer.class.
                    getResourceAsStream("/org/tso/hexviewer.ui");

            ByteArrayOutputStream output = new ByteArrayOutputStream();

            byte[] buffer = new byte[1024];

            for (int length; (length = inputStream.read(buffer)) != -1;) {
                output.write(buffer, 0, length);
            }

            var uiDefinition = output.toString("UTF-8");

            System.out.println(uiDefinition);

            builder.addFromString(uiDefinition, uiDefinition.length());

            window = (Window) builder.getObject("main");

            var openToolbarButton = (Button) builder.getObject("openToolbarButton");

            openToolbarButton.onClicked(this::open);

            var columnView = (ColumnView) builder.getObject("hexViewer");

            columnView.setShowColumnSeparators(true);

            store = new ListStore<>(Row.gtype);

            setupColumns(columnView);
            
            columnView.setModel(new NoSelection<Row>(store));

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
