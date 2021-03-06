package com.youdian.soundeffects.remotepcdroidserver;

import com.youdian.soundeffects.RemotePCDroidServerApp;
import com.youdian.soundeffects.protocol.RemotePCDroidConnection;
import com.youdian.soundeffects.protocol.action.*;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ProtocolException;
import java.util.ArrayList;

/**
 * @author hkq
 */
public class RemotePCDroidServerConnection implements Runnable {
    private RemotePCDroidServerApp application;

    private RemotePCDroidConnection connection;

    private boolean authenticated;

    private String os = System.getProperty("os.name");

    private String cmd;

    private Robot robot;

    public RemotePCDroidServerConnection(RemotePCDroidServerApp application, RemotePCDroidConnection connection) {
        this.application = application;
        this.connection = connection;

        this.authenticated = false;

        try {
            this.robot = new Robot();
        } catch (Exception e) {
            e.printStackTrace();
        }

        (new Thread(this)).start();
    }

    @Override
    public void run() {
        try {
            try {
                while (true) {
                    RemotePCDroidAction action = this.connection.receiveAction();

                    this.action(action);
                }
            } finally {
                this.connection.close();
            }
        } catch (ProtocolException e) {
            e.printStackTrace();

            this.application.getTrayIcon().notifyProtocolProblem();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void action(RemotePCDroidAction action) {
        if (this.authenticated) {
            if (action instanceof FileExploreRequestAction) {
                this.fileExplore((FileExploreRequestAction) action);
            } else if (action instanceof ShutDownAction) {
                try {
                    this.shutDownServer((ShutDownAction) action);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (action instanceof RebootAction) {
                try {
                    this.rebootServer((RebootAction) action);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (action instanceof HibernateAction) {
                try {
                    this.HibernateServer((HibernateAction) action);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (action instanceof MouseKeyboardAction) {
                this.MouseKbd((MouseKeyboardAction) action);
            }
        } else {
            if (action instanceof AuthenticationAction) {
                this.authentificate((AuthenticationAction) action);
            }

            if (!this.authenticated) {
                try {
                    this.connection.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void MouseKbd(MouseKeyboardAction action) {
        switch (action.choice) {
            // 空格键
            case 0:
                robot.keyPress(KeyEvent.VK_SPACE);
                robot.keyRelease(KeyEvent.VK_SPACE);
                robot.delay(500);
                break;
            // ESC键
            case 1:
                robot.keyPress(KeyEvent.VK_ESCAPE);
                robot.keyRelease(KeyEvent.VK_ESCAPE);
                robot.delay(500);
                break;
            // 回车键
            case 2:
                robot.keyPress(KeyEvent.VK_ENTER);
                robot.keyRelease(KeyEvent.VK_ENTER);
                robot.delay(500);
                break;
            // alt enter
            case 3:
                robot.keyPress(KeyEvent.VK_ALT);
                robot.keyPress(KeyEvent.VK_ENTER);
                robot.keyRelease(KeyEvent.VK_ALT);
                robot.keyRelease(KeyEvent.VK_ENTER);
                robot.delay(500);
                break;
            // alt f4
            case 4:
                robot.keyPress(KeyEvent.VK_ALT);
                robot.keyPress(KeyEvent.VK_F4);
                robot.keyRelease(KeyEvent.VK_ALT);
                robot.keyRelease(KeyEvent.VK_F4);
                robot.delay(500);
                break;
            // 鼠标单击
            case 5:
                robot.mousePress(InputEvent.BUTTON1_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_MASK);
                robot.delay(1000);
                break;
            // 鼠标双击
            case 6:
                robot.mousePress(InputEvent.BUTTON1_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_MASK);
                robot.mousePress(InputEvent.BUTTON1_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_MASK);
                robot.delay(1000);
                break;
        }

    }

    private void authentificate(AuthenticationAction action) {
        if (action.password.equals(this.application.getPreferences().get("password", RemotePCDroidConnection.DEFAULT_PASSWORD))) {
            this.authenticated = true;

            this.application.getTrayIcon().notifyConnection(this.connection);
        }

        this.sendAction(new AuthenticationResponseAction(this.authenticated));
    }

    private void shutDownServer(ShutDownAction action) throws IOException {
        int t = action.time;
        if (os.contains("Linux") || os.contains("Mac OS X")) {
            cmd = "shutdown -h " + t;
        } else if (os.contains("Windows")) {
            cmd = "shutdown.exe -f -s -t " + t;
        } else {
            throw new RuntimeException("不支持的操作系统.");
        }
        Runtime.getRuntime().exec(cmd);
    }

    private void rebootServer(RebootAction action) throws IOException {
        int t = action.time;
        if (os.contains("Linux") || os.contains("Mac OS X")) {
            cmd = "shutdown -r " + t;
        } else if (os.contains("Windows")) {
            cmd = "shutdown.exe -f -r -t " + t;
        } else {
            throw new RuntimeException("不支持的操作系统.");
        }
        Runtime.getRuntime().exec(cmd);
    }

    private void HibernateServer(HibernateAction action) throws IOException {
        if (os.contains("Linux") || os.contains("Mac OS X")) {
            cmd = "pm-hibernate";
        } else if (os.contains("Windows")) {
            cmd = "shutdown.exe -h";
        } else {
            throw new RuntimeException("不支持的操作系统.");
        }
        Runtime.getRuntime().exec(cmd);
    }

    private void fileExplore(FileExploreRequestAction action) {
        if (action.directory.isEmpty() && action.file.isEmpty()) {
            this.fileExploreRoots();
        } else {
            if (action.directory.isEmpty()) {
                this.fileExplore(new File(action.file));
            } else {
                File directory = new File(action.directory);

                if (directory.getParent() == null && action.file.equals("..")) {
                    this.fileExploreRoots();
                } else {
                    try {
                        this.fileExplore(new File(directory, action.file).getCanonicalFile());
                    } catch (IOException e) {
                        e.printStackTrace();

                        this.fileExploreRoots();
                    }
                }
            }
        }
    }

    private void fileExplore(File file) {
        if (file.exists() && file.canRead()) {
            if (file.isDirectory()) {
                this.sendFileExploreResponse(file.getAbsolutePath(), file.listFiles(), true);
            } else {
                if (Desktop.isDesktopSupported()) {
                    Desktop desktop = Desktop.getDesktop();

                    if (desktop.isSupported(Desktop.Action.OPEN)) {
                        try {
                            desktop.open(file);
                        } catch (IOException e) {
                            e.printStackTrace();


                            if (RemotePCDroidServerApp.os_type.contains("windows")) {
                                System.out.println("windows cmd fix");

                                try {
                                    Process process = Runtime.getRuntime().exec("cmd /C " + file.getAbsolutePath());
                                    BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));

                                    String line;
                                    while ((line = br.readLine()) != null) {
                                        System.out.println(line);
                                    }
                                } catch (IOException e1) {
                                    e1.printStackTrace();
                                }
                            }
                        }
                    }
                }
            }
        } else {
            this.fileExploreRoots();
        }
    }

    private void fileExploreRoots() {
        String directory = "";

        File[] files = File.listRoots();

        this.sendFileExploreResponse(directory, files, false);
    }

    private void sendFileExploreResponse(String directory, File[] f, boolean parent) {
        if (f != null) {
            ArrayList<String> list = new ArrayList<String>();

            if (parent) {
                list.add("..");
            }

            for (int i = 0; i < f.length; i++) {
                String name = f[i].getName();

                if (!name.isEmpty()) {
                    if (f[i].isDirectory()) {
                        name += File.separator;
                    }
                } else {
                    name = f[i].getAbsolutePath();
                }

                list.add(name);
            }

            String[] files = new String[list.size()];

            files = list.toArray(files);

            this.sendAction(new FileExploreResponseAction(directory, files));
        }
    }

    private void sendAction(RemotePCDroidAction action) {
        try {
            this.connection.sendAction(action);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
