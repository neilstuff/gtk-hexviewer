package org.tso;

import org.gnome.gtk.Button;
import org.gnome.gtk.GtkBuilder;
import org.gnome.gtk.TextBuffer;
import org.gnome.gtk.TextIter;
import org.gnome.gtk.TextView;
import org.gnome.gtk.Window;
import org.tso.util.GuiUtils;


public class AboutDialog {

   
    Window window;
    GtkBuilder builder;

    AboutDialog(Window parent, final String definition) throws Exception {

        builder = new GtkBuilder();

        var uiDefinition = GuiUtils.getDefintion(definition);

        builder.addFromString(uiDefinition, uiDefinition.length());

        this.window = (Window) builder.getObject("infoDialog");

        this.window.setParent(parent);

        var okButtton = (Button) builder.getObject("button_ok");

        okButtton.onClicked(window::close);

        TextView infoView = (TextView)builder.getObject("infoViewer");

        TextBuffer buffer = new TextBuffer();
        TextIter   iter = new TextIter();
        buffer.getStartIter(iter);

        String description = "<span weight=\"ultraheavy\" size=\"x-large\">Hex Viewer </span> \n\n" + 
                    "<b>Version:</b> 1.0 \n" +
                    "<b>Author:</b> Dr. Neil \n" +
                    "<b>Home Page: </b> www.brittliff.org \n\n" +
                    "<span weight=\"ultraheavy\" size=\"large\">Acknowledgements </span> \n\n" + 
                    " - \tThe GTK Team \n" +
                    " - \tThe team that used incorporated the Panama API \n" +
                    "\t to allow Java to have a decent GUI \n\n" + 
                    "(c) The code can be used in fair-use for any purpose.";
                                
            buffer.insertMarkup(iter, description, -1);
            infoView.setBuffer(buffer);
       
    }

    void show() {

        this.window.setVisible(true);

    }

}
