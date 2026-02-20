package com.runeliteagent;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

@Slf4j
public class ClaudeAgentPanel extends PluginPanel
{
    private static final Color USER_BG = new Color(44, 62, 80);
    private static final Color ASSISTANT_BG = new Color(30, 39, 46);
    private static final Color TOOL_BG = new Color(39, 55, 40);
    private static final Color TEXT_COLOR = new Color(236, 240, 241);
    private static final Color MUTED_COLOR = new Color(149, 165, 166);
    private static final Color ACCENT_COLOR = new Color(52, 152, 219);
    private static final Font MSG_FONT = new Font("SansSerif", Font.PLAIN, 12);
    private static final Font LABEL_FONT = new Font("SansSerif", Font.BOLD, 11);

    private final AgentOrchestrator orchestrator;
    private final ClaudeAgentConfig config;

    private JPanel chatContainer;
    private JScrollPane chatScroll;
    private JTextArea inputArea;
    private JButton sendButton;
    private JButton clearButton;
    private JPanel currentAssistantBubble;
    private JTextArea currentAssistantText;
    private boolean isProcessing = false;

    public ClaudeAgentPanel(AgentOrchestrator orchestrator, ClaudeAgentConfig config)
    {
        super(false);
        this.orchestrator = orchestrator;
        this.config = config;
        buildUI();
    }

    private void buildUI()
    {
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        header.setBorder(new EmptyBorder(8, 10, 8, 10));

        JLabel title = new JLabel("Claude Agent");
        title.setForeground(TEXT_COLOR);
        title.setFont(new Font("SansSerif", Font.BOLD, 14));
        header.add(title, BorderLayout.WEST);

        clearButton = new JButton("Clear");
        clearButton.setFont(LABEL_FONT);
        clearButton.setForeground(MUTED_COLOR);
        clearButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        clearButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(MUTED_COLOR, 1),
            new EmptyBorder(4, 8, 4, 8)
        ));
        clearButton.setFocusPainted(false);
        clearButton.addActionListener(e -> clearChat());
        header.add(clearButton, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        // Chat area
        chatContainer = new JPanel();
        chatContainer.setLayout(new BoxLayout(chatContainer, BoxLayout.Y_AXIS));
        chatContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        chatContainer.setBorder(new EmptyBorder(5, 5, 5, 5));

        chatScroll = new JScrollPane(chatContainer);
        chatScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        chatScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        chatScroll.setBorder(null);
        chatScroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        add(chatScroll, BorderLayout.CENTER);

        // Input area
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        inputPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        inputArea = new JTextArea(3, 20);
        inputArea.setFont(MSG_FONT);
        inputArea.setForeground(TEXT_COLOR);
        inputArea.setBackground(new Color(44, 62, 80));
        inputArea.setCaretColor(TEXT_COLOR);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        inputArea.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown())
                {
                    e.consume();
                    sendMessage();
                }
            }
        });

        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createLineBorder(new Color(52, 73, 94), 1));
        inputScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        inputPanel.add(inputScroll, BorderLayout.CENTER);

        sendButton = new JButton("Send");
        sendButton.setFont(LABEL_FONT);
        sendButton.setForeground(Color.WHITE);
        sendButton.setBackground(ACCENT_COLOR);
        sendButton.setBorder(new EmptyBorder(8, 14, 8, 14));
        sendButton.setFocusPainted(false);
        sendButton.setPreferredSize(new Dimension(60, 0));
        sendButton.addActionListener(e -> sendMessage());
        inputPanel.add(sendButton, BorderLayout.EAST);

        add(inputPanel, BorderLayout.SOUTH);

        // Welcome message
        addSystemMessage("Welcome! I'm your Claude-powered OSRS assistant.\n\n"
            + "I can help you with:\n"
            + "• Toggle RuneLite plugins on/off\n"
            + "• Change plugin settings\n"
            + "• Look up OSRS Wiki info\n"
            + "• Answer game questions\n\n"
            + "Set your API key in the plugin config to get started.");
    }

    private void sendMessage()
    {
        String text = inputArea.getText().trim();
        if (text.isEmpty() || isProcessing)
        {
            return;
        }

        String apiKey = config.apiKey();
        if (apiKey == null || apiKey.isEmpty())
        {
            addSystemMessage("⚠️ Please set your Claude API key in the plugin settings first.");
            return;
        }

        isProcessing = true;
        sendButton.setEnabled(false);
        sendButton.setText("...");
        inputArea.setText("");

        addUserMessage(text);
        currentAssistantBubble = null;
        currentAssistantText = null;

        orchestrator.sendMessage(text,
            // onChunk - append text as it arrives
            chunk -> SwingUtilities.invokeLater(() -> {
                if (chunk.startsWith("\n🔧"))
                {
                    addToolMessage(chunk.trim());
                }
                else
                {
                    appendToAssistantMessage(chunk);
                }
            }),
            // onComplete
            complete -> SwingUtilities.invokeLater(() -> {
                isProcessing = false;
                sendButton.setEnabled(true);
                sendButton.setText("Send");
                scrollToBottom();
            }),
            // onError
            error -> SwingUtilities.invokeLater(() -> {
                addSystemMessage("❌ " + error);
                isProcessing = false;
                sendButton.setEnabled(true);
                sendButton.setText("Send");
            })
        );
    }

    private void addUserMessage(String text)
    {
        JPanel bubble = createBubble("You", text, USER_BG, ACCENT_COLOR);
        chatContainer.add(bubble);
        chatContainer.revalidate();
        scrollToBottom();
    }

    private void appendToAssistantMessage(String text)
    {
        if (currentAssistantBubble == null)
        {
            currentAssistantText = new JTextArea();
            currentAssistantText.setFont(MSG_FONT);
            currentAssistantText.setForeground(TEXT_COLOR);
            currentAssistantText.setBackground(ASSISTANT_BG);
            currentAssistantText.setEditable(false);
            currentAssistantText.setLineWrap(true);
            currentAssistantText.setWrapStyleWord(true);
            currentAssistantText.setBorder(null);

            currentAssistantBubble = new JPanel(new BorderLayout());
            currentAssistantBubble.setBackground(ASSISTANT_BG);
            currentAssistantBubble.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(3, 0, 3, 0),
                new EmptyBorder(8, 10, 8, 10)
            ));
            currentAssistantBubble.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

            JLabel label = new JLabel("Claude");
            label.setFont(LABEL_FONT);
            label.setForeground(new Color(46, 204, 113));
            label.setBorder(new EmptyBorder(0, 0, 4, 0));
            currentAssistantBubble.add(label, BorderLayout.NORTH);
            currentAssistantBubble.add(currentAssistantText, BorderLayout.CENTER);

            chatContainer.add(currentAssistantBubble);
            chatContainer.revalidate();
        }

        currentAssistantText.append(text);
        chatContainer.revalidate();
        scrollToBottom();
    }

    private void addToolMessage(String text)
    {
        JPanel bubble = createBubble("Tool", text, TOOL_BG, new Color(241, 196, 15));
        chatContainer.add(bubble);
        chatContainer.revalidate();
        scrollToBottom();
    }

    private void addSystemMessage(String text)
    {
        JPanel bubble = createBubble("System", text, ColorScheme.DARKER_GRAY_COLOR, MUTED_COLOR);
        chatContainer.add(bubble);
        chatContainer.revalidate();
        scrollToBottom();
    }

    private JPanel createBubble(String sender, String text, Color bg, Color labelColor)
    {
        JPanel bubble = new JPanel(new BorderLayout());
        bubble.setBackground(bg);
        bubble.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(3, 0, 3, 0),
            new EmptyBorder(8, 10, 8, 10)
        ));
        bubble.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel label = new JLabel(sender);
        label.setFont(LABEL_FONT);
        label.setForeground(labelColor);
        label.setBorder(new EmptyBorder(0, 0, 4, 0));
        bubble.add(label, BorderLayout.NORTH);

        JTextArea content = new JTextArea(text);
        content.setFont(MSG_FONT);
        content.setForeground(TEXT_COLOR);
        content.setBackground(bg);
        content.setEditable(false);
        content.setLineWrap(true);
        content.setWrapStyleWord(true);
        content.setBorder(null);
        bubble.add(content, BorderLayout.CENTER);

        return bubble;
    }

    private void scrollToBottom()
    {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = chatScroll.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private void clearChat()
    {
        chatContainer.removeAll();
        chatContainer.revalidate();
        chatContainer.repaint();
        currentAssistantBubble = null;
        currentAssistantText = null;
        orchestrator.clearHistory();
        addSystemMessage("Chat cleared. Ready for a new conversation.");
    }
}
