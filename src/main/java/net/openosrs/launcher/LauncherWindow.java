package net.openosrs.launcher;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.WindowConstants;
import javax.swing.plaf.basic.BasicProgressBarUI;

/** Compact desktop surface; release selection and launch ownership stay in Launcher. */
final class LauncherWindow extends JFrame
{
    private static final Color INK = new Color(0x171E27);
    private static final Color SLATE = new Color(0x222D3A);
    private static final Color LINE = new Color(0x354150);
    private static final Color ACCENT = new Color(0x1688EE);
    private static final Color TEXT = new Color(0xE9EDF0);
    private static final Color MUTED = new Color(0xA5B1C0);
    private static final Color ERROR = new Color(0xEEA298);
    private static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private final JLabel versions = label("Checking client version", 11, MUTED);
    private final JTextArea status = new JTextArea("Checking for updates…");
    private final JButton launch = new ActionButton("Launch OpenOSRS", true);
    private final JButton check = new ActionButton("Check updates", false);
    private final JButton update = new ActionButton("Update launcher", false);
    private final JProgressBar progress = new JProgressBar();

    LauncherWindow(Runnable startClient, Runnable checkUpdates, Runnable updateLauncher, Runnable openLogs, Runnable onClose)
    {
        super("OpenOSRS Launcher");
        setUndecorated(true);
        setResizable(false);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        boolean translucent = getGraphicsConfiguration().getDevice()
            .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT);
        if (translucent) { setBackground(new Color(0, 0, 0, 0)); }

        JPanel shell = new JPanel(new BorderLayout())
        {
            @Override protected void paintComponent(Graphics graphics)
            {
                Graphics2D g = smooth(graphics);
                g.setPaint(new GradientPaint(0, 0, SLATE, getWidth(), getHeight(), INK));
                if (translucent) { g.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20); }
                else { g.fillRect(0, 0, getWidth(), getHeight()); }
                g.setColor(LINE);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
                g.dispose();
            }
        };
        shell.setOpaque(false);
        shell.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        setContentPane(shell);

        JPanel header = transparent(new BorderLayout(10, 0));
        header.setPreferredSize(new Dimension(478, 88));
        header.setBorder(BorderFactory.createEmptyBorder(19, 22, 13, 12));
        BufferedImage logo = readLogo();
        JLabel mark = new JLabel(new ImageIcon(logo.getScaledInstance(56, 56, java.awt.Image.SCALE_SMOOTH)));
        JPanel identity = transparent(new BorderLayout(13, 0));
        identity.add(mark, BorderLayout.WEST);
        JPanel wordmark = transparent(new BorderLayout(0, 3));
        JLabel title = label("OpenOSRS", 23, TEXT);
        title.setFont(BODY.deriveFont(Font.BOLD, 23f));
        wordmark.add(title, BorderLayout.CENTER);
        wordmark.add(versions, BorderLayout.SOUTH);
        identity.add(wordmark, BorderLayout.CENTER);
        header.add(identity, BorderLayout.WEST);
        JPanel chrome = transparent(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        JButton minimize = new ChromeButton(false);
        minimize.addActionListener(event -> setState(ICONIFIED));
        JButton close = new ChromeButton(true);
        close.addActionListener(event -> dispose());
        chrome.add(minimize);
        chrome.add(close);
        header.add(chrome, BorderLayout.EAST);
        shell.add(header, BorderLayout.NORTH);

        MouseAdapter drag = new MouseAdapter()
        {
            private Point offset;
            @Override public void mousePressed(MouseEvent event)
            {
                if (event.getButton() == MouseEvent.BUTTON1)
                {
                    offset = new Point(event.getXOnScreen() - getX(), event.getYOnScreen() - getY());
                }
            }
            @Override public void mouseDragged(MouseEvent event)
            {
                if (offset != null) { setLocation(event.getXOnScreen() - offset.x, event.getYOnScreen() - offset.y); }
            }
            @Override public void mouseReleased(MouseEvent event) { offset = null; }
        };
        for (Component component : new Component[]{header, identity, wordmark, mark, title, versions})
        {
            component.addMouseListener(drag);
            component.addMouseMotionListener(drag);
        }

        JPanel body = transparent(new BorderLayout(0, 12));
        body.setBorder(BorderFactory.createEmptyBorder(0, 24, 18, 24));
        status.setFont(BODY);
        status.setForeground(MUTED);
        status.setOpaque(false);
        status.setEditable(false);
        status.setFocusable(true);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        status.getAccessibleContext().setAccessibleName("Launcher status");
        JScrollPane messages = new JScrollPane(status, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        messages.setBorder(BorderFactory.createEmptyBorder());
        messages.setOpaque(false);
        messages.getViewport().setOpaque(false);
        body.add(messages, BorderLayout.CENTER);
        launch.setPreferredSize(new Dimension(430, 46));
        launch.setEnabled(false);
        launch.addActionListener(event -> startClient.run());
        body.add(launch, BorderLayout.SOUTH);
        shell.add(body, BorderLayout.CENTER);
        getRootPane().setDefaultButton(launch);

        JPanel footer = transparent(new BorderLayout());
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, LINE));
        JPanel actions = transparent(new BorderLayout());
        actions.setPreferredSize(new Dimension(478, 44));
        actions.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 22));
        JPanel links = transparent(null);
        links.setLayout(new BoxLayout(links, BoxLayout.X_AXIS));
        JButton logs = new ActionButton("Logs", false);
        logs.addActionListener(event -> openLogs.run());
        check.addActionListener(event -> checkUpdates.run());
        update.addActionListener(event -> updateLauncher.run());
        update.setForeground(ACCENT);
        update.setVisible(false);
        links.add(logs);
        links.add(Box.createHorizontalStrut(4));
        links.add(check);
        links.add(Box.createHorizontalStrut(4));
        links.add(update);
        actions.add(links, BorderLayout.WEST);
        JLabel build = label("v" + Launcher.version(), 10, MUTED);
        build.setToolTipText("Launcher " + Launcher.version());
        actions.add(build, BorderLayout.EAST);
        footer.add(actions, BorderLayout.CENTER);
        progress.setUI(new BasicProgressBarUI());
        progress.setBorderPainted(false);
        progress.setForeground(ACCENT);
        progress.setBackground(INK);
        progress.setPreferredSize(new Dimension(478, 2));
        footer.add(progress, BorderLayout.SOUTH);
        shell.add(footer, BorderLayout.SOUTH);

        setIconImage(logo);
        addWindowListener(new WindowAdapter()
        {
            @Override public void windowClosed(WindowEvent event)
            {
                progress.setIndeterminate(false);
                onClose.run();
            }
        });
        setSize(480, 264);
        setLocationRelativeTo(null);
    }

    void setClientVersion(String version, int revision)
    {
        versions.setText("Client " + version + "   /   Revision " + revision);
    }

    void setBusy(boolean busy, boolean clientAvailable, boolean updateAvailable)
    {
        progress.setIndeterminate(busy);
        check.setEnabled(!busy);
        launch.setEnabled(!busy && clientAvailable);
        update.setEnabled(!busy);
        update.setVisible(updateAvailable);
    }

    void setStatus(String message, boolean error)
    {
        status.setText(message);
        status.setCaretPosition(0);
        status.setForeground(error ? ERROR : MUTED);
    }

    private static JPanel transparent(java.awt.LayoutManager layout)
    {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private static JLabel label(String value, int size, Color color)
    {
        JLabel label = new JLabel(value)
        {
            @Override protected void paintComponent(Graphics graphics)
            {
                Graphics2D g = smooth(graphics);
                super.paintComponent(g);
                g.dispose();
            }
        };
        label.setFont(BODY.deriveFont((float) size));
        label.setForeground(color);
        return label;
    }

    private static Graphics2D smooth(Graphics graphics)
    {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    private static BufferedImage readLogo()
    {
        try
        {
            BufferedImage image = ImageIO.read(LauncherWindow.class.getResource("logo.png"));
            if (image == null) { throw new IOException("Invalid launcher logo"); }
            return image;
        }
        catch (IOException exception) { throw new IllegalStateException("Could not load the launcher logo", exception); }
    }

    private static class ActionButton extends JButton
    {
        private final boolean primary;
        ActionButton(String title, boolean primary)
        {
            super(title);
            this.primary = primary;
            setFont(BODY.deriveFont(primary ? Font.BOLD : Font.PLAIN, primary ? 13f : 11f));
            setForeground(primary ? Color.WHITE : MUTED);
            setBorder(BorderFactory.createEmptyBorder(8, 9, 8, 9));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override protected void paintComponent(Graphics graphics)
        {
            Graphics2D g = smooth(graphics);
            int width = getWidth(), height = getHeight();
            boolean hover = getModel().isRollover() && isEnabled();
            if (primary)
            {
                Color top = !isEnabled() ? LINE : getModel().isPressed() ? ACCENT.darker() : hover ? new Color(0x2999FF) : new Color(0x1688EE);
                Color bottom = !isEnabled() ? SLATE : hover ? ACCENT : new Color(0x086AC5);
                g.setPaint(new GradientPaint(0, 0, top, 0, height, bottom));
                g.fillRoundRect(0, 0, width, height, 10, 10);
                g.setColor(isEnabled() ? new Color(0x46A4F8) : LINE);
                g.drawRoundRect(0, 0, width - 1, height - 1, 10, 10);
            }
            else if (hover || getModel().isPressed())
            {
                g.setColor(LINE); g.fillRoundRect(0, 0, width, height, 7, 7);
            }
            if (isFocusOwner())
            {
                g.setColor(primary ? Color.WHITE : ACCENT);
                g.drawRoundRect(3, 3, width - 7, height - 7, 6, 6);
            }
            g.setFont(getFont());
            g.setColor(isEnabled() ? getForeground() : MUTED.darker());
            int textWidth = g.getFontMetrics().stringWidth(getText());
            g.drawString(getText(), (width - textWidth) / 2, (height - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent());
            if (primary)
            {
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int end = width - 22, center = height / 2;
                g.drawLine(end - 12, center, end, center);
                g.drawLine(end - 5, center - 5, end, center);
                g.drawLine(end - 5, center + 5, end, center);
            }
            g.dispose();
        }
    }

    private static final class ChromeButton extends JButton
    {
        private final boolean close;
        ChromeButton(boolean close)
        {
            this.close = close;
            setToolTipText(close ? "Close launcher" : "Minimize launcher");
            getAccessibleContext().setAccessibleName(getToolTipText());
            setPreferredSize(new Dimension(28, 26));
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
        }

        @Override protected void paintComponent(Graphics graphics)
        {
            Graphics2D g = smooth(graphics);
            if (getModel().isRollover() || isFocusOwner())
            {
                g.setColor(close ? new Color(0x653F43) : LINE);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
            }
            g.setColor(MUTED);
            g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (close) { g.drawLine(10, 9, 18, 17); g.drawLine(10, 17, 18, 9); }
            else { g.drawLine(10, 14, 18, 14); }
            g.dispose();
        }
    }
}
