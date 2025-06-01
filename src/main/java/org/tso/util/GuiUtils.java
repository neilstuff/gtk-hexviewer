package org.tso.util;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

import org.gnome.gtk.Inscription;
import org.gnome.gtk.ListItem;
import org.gnome.gtk.SignalListItemFactory;

public class GuiUtils {
    private static final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();

    static final public String getDefintion(String definition) throws Exception {
        InputStream inputStream = GuiUtils.class.
                getResourceAsStream(definition);

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        byte[] buffer = new byte[1024];

        for (int length; (length = inputStream.read(buffer)) != -1;) {
            output.write(buffer, 0, length);
        }

        return output.toString("UTF-8");

    }

    static final public ArrayList<Object> asHex(byte[] buf) {

        var values = new ArrayList<Object>(2);

        StringBuffer asciiChars = new StringBuffer();

        String[] hex = new String[16];

        for (int iHex = 0; iHex < hex.length; iHex++) {
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

     static final public SignalListItemFactory createSignalListItemFactory() {

        var columnFactory = new SignalListItemFactory();

        columnFactory.onSetup(item -> {
            var listitem = (ListItem) item;
            var inscription = Inscription.builder()
                    .setXalign(0)
                    .build();
            listitem.setChild(inscription);

        });

        return columnFactory;

    }

}
