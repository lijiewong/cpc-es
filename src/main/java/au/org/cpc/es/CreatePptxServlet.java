package au.org.cpc.es;

import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.poi.xslf.usermodel.SlideLayout;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFSlideMaster;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

public class CreatePptxServlet extends HttpServlet {
    static private class MakeSlides {
        private XMLSlideShow show;
        private XSLFSlideLayout blankLayout;
        private XSLFSlideLayout textLayout;

        public MakeSlides(XMLSlideShow show) {
            while (!show.getSlides().isEmpty()) {
                show.removeSlide(0);
            }
            this.show = show;

            List<XSLFSlideMaster> masters = show.getSlideMasters();
            XSLFSlideMaster master = masters.get(masters.size() - 1);

            this.blankLayout = master.getLayout(SlideLayout.BLANK);
            this.textLayout = master.getLayout(SlideLayout.TEXT);
        }

        private enum State {
            PAGE_BREAK,
            TITLE,
            BODY,
            CREDITS,
        }

        public void generate(java.io.BufferedReader reader) throws IOException {
            State state = State.PAGE_BREAK;
            XSLFSlide slide = null;

            for (;;) {
                String line = reader.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) {
                    if (state == State.PAGE_BREAK) {
                        show.createSlide(blankLayout);
                    }
                    state = State.PAGE_BREAK;
                    continue;
                }

                if (state == State.PAGE_BREAK) {
                    slide = show.createSlide(textLayout);
                    state = State.BODY;
                }
                if (line.equals("##title")) {
                    state = State.TITLE;
                    continue;
                } else if (line.equals("##credits")) {
                    state = State.CREDITS;
                    addText(slide.getPlaceholder(1), " ", 0.5);
                    continue;
                }

                switch (state) {
                case TITLE:
                    addText(slide.getPlaceholder(0), line, 1.0);
                    state = State.BODY;
                    break;
                case BODY:
                    addText(slide.getPlaceholder(1), line, 1.0);
                    break;
                case CREDITS:
                    addText(slide.getPlaceholder(1), line, 0.5);
                    break;
                default:
                    throw new IllegalStateException();
                }
            }
        }

        private static void addText(XSLFTextShape shape, String line, double sizeRatio) {
            XSLFTextParagraph para = shape.addNewTextParagraph();
            para.setBullet(false);
            para.setIndentLevel(0);
            XSLFTextRun run = para.addNewTextRun();
            run.setFontSize(run.getFontSize() * sizeRatio);
            run.setText(line);
        }
    }

    private byte[] pptxTemplate;

    @Override
    public void init() throws ServletException {
        try {
            java.io.InputStream is = getServletContext()
                .getResourceAsStream("/WEB-INF/template.pptx");
            pptxTemplate = org.apache.commons.io.IOUtils.toByteArray(is);
        } catch (IOException e) {
            throw new ServletException(e);
        }
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        XMLSlideShow show = new XMLSlideShow(new java.io.ByteArrayInputStream(pptxTemplate));
        new MakeSlides(show).generate(req.getReader());
        resp.setContentType("application/octet-stream");
        show.write(resp.getOutputStream());
    }
}