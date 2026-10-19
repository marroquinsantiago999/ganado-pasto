import javax.swing.*;
import java.awt.*;

public class PasturaGanaderia extends JFrame {

    static class Nodo<T> {
        T dato;
        Nodo<T> sig, ant;
        Nodo(T dato) { this.dato = dato; }
    }

    static class ListaSimple<T> {
        Nodo<T> cabeza, cola;
        int tam = 0;

        void agregar(T d) {
            Nodo<T> n = new Nodo<>(d);
            if (cabeza == null) cabeza = cola = n;   // vacia: el nodo es cabeza y cola a la vez
            else { cola.sig = n; cola = n; }         // se cuelga al final
            tam++;
        }
    }

    static class ListaDoble<T> {
        Nodo<T> cabeza, cola, actual;
        int tam = 0, pos = 0;

        void agregar(T d) {
            Nodo<T> n = new Nodo<>(d);
            if (cabeza == null) cabeza = cola = actual = n;
            else { n.ant = cola; cola.sig = n; cola = n; }   // enlace en los dos sentidos
            tam++;
        }
        // no se mueve si ya esta en un extremo
        void siguiente() { if (actual.sig != null) { actual = actual.sig; pos++; } }
        void anterior()  { if (actual.ant != null) { actual = actual.ant; pos--; } }
    }

    // solo guardo 'ultimo', el primero es ultimo.sig. pos sirve para que el escenario sepa a que potrero volver
    static class ListaCircular<T> {
        Nodo<T> ultimo, actual;
        int tam = 0, pos = 0;

        void agregar(T d) {
            Nodo<T> n = new Nodo<>(d);
            if (ultimo == null) { n.sig = n; ultimo = actual = n; }    // unico nodo: se apunta a si mismo
            else { n.sig = ultimo.sig; ultimo.sig = n; ultimo = n; }   // el nuevo cierra el circulo apuntando al primero
            tam++;
        }
        // despues del ultimo vuelve al primero, por eso el modulo en pos
        void avanzar() { actual = actual.sig; pos = (pos + 1) % tam; }
    }

    static class Pila<T> {
        Nodo<T> tope;
        int tam = 0;

        void apilar(T d) {
            Nodo<T> n = new Nodo<>(d);
            n.sig = tope;   // el nuevo queda encima del tope anterior
            tope = n;
            tam++;
        }
        T desapilar() {
            if (tope == null) return null;   // pila vacia
            T d = tope.dato;
            tope = tope.sig;
            tam--;
            return d;
        }
    }

    static class Cola<T> {
        Nodo<T> frente, fin;
        int tam = 0;

        void encolar(T d) {
            Nodo<T> n = new Nodo<>(d);
            if (fin == null) frente = fin = n;
            else { fin.sig = n; fin = n; }   // entra siempre por el fin
            tam++;
        }
        T desencolar() {
            if (frente == null) return null;   // cola vacia
            T d = frente.dato;
            frente = frente.sig;
            if (frente == null) fin = null;   // se vacio, tambien limpio el fin
            tam--;
            return d;
        }
        // simular desencola, asi que trabajo sobre una copia y la cola real queda intacta
        Cola<T> copiar() {
            Cola<T> c = new Cola<>();
            for (Nodo<T> x = frente; x != null; x = x.sig) c.encolar(x.dato);
            return c;
        }
    }

    record Potrero(String nombre, double k, double pasto0) { }
    record Suplemento(String nombre, double efecto) { }   // efecto = fraccion de la dieta que cubre
    record Evento(String nombre, int dia, int duracion, double factorR) { }   // factorR multiplica a r mientras dura
    record Censo(int dia, double pasto, double ganado) { }
    // pot y sup = posicion en la lista circular y en la doble
    record Escenario(double r, double k, double m, double p0, double g0, int tg, int tp, int pot, int sup) { }

    // fila = tipo de ganado, columna = tipo de pasto
    static final String[] GANADO = {"Vaca lechera", "Novillo", "Ternero"};
    static final String[] PASTO  = {"Kikuyo", "Brachiaria", "Estrella"};
    static final double[][] CONSUMO = {
        {0.0030, 0.0032, 0.0028},
        {0.0025, 0.0027, 0.0023},
        {0.0015, 0.0016, 0.0014}
    };
    static final double[][] CONVERSION = {
        {5.0e-6, 5.5e-6, 4.5e-6},
        {4.5e-6, 5.0e-6, 4.0e-6},
        {4.0e-6, 4.5e-6, 3.5e-6}
    };

    ListaCircular<Potrero> potreros = new ListaCircular<>();
    ListaDoble<Suplemento> suplementos = new ListaDoble<>();
    Pila<Escenario> escenarios = new Pila<>();
    Cola<Evento> eventos = new Cola<>();
    ListaSimple<Censo> censos = new ListaSimple<>();
    ListaSimple<int[]> bandas = new ListaSimple<>();   // {inicio, fin, 1 si es adverso}, solo para pintar

    double[] pasto, ganado;   // un valor por dia
    double umbral;   // 25% de K

    JTextField tfR = new JTextField("0.10"), tfK = new JTextField(), tfM = new JTextField("0.01");
    JTextField tfP0 = new JTextField(), tfG0 = new JTextField("12"), tfDias = new JTextField("365");
    JTextField tfDiaEv = new JTextField("100"), tfDurEv = new JTextField("60");
    JComboBox<String> cbGanado = new JComboBox<>(GANADO), cbPasto = new JComboBox<>(PASTO);
    JComboBox<String> cbEvento = new JComboBox<>(new String[]{"Sequía", "Invierno", "Lluvias intensas"});
    JLabel lblPotrero = new JLabel(), lblSuplemento = new JLabel();
    JTextArea resultados = new JTextArea();
    Grafica grafica = new Grafica();

    public PasturaGanaderia() {
        super("Simulador de Pastura y Ganadería - Lotka-Volterra");

        potreros.agregar(new Potrero("Potrero Norte", 5000, 3000));
        potreros.agregar(new Potrero("Potrero Río", 6000, 4000));
        potreros.agregar(new Potrero("Potrero Loma", 3500, 2000));
        potreros.agregar(new Potrero("Potrero Sur", 4500, 3500));

        suplementos.agregar(new Suplemento("Ninguno", 0.00));
        suplementos.agregar(new Suplemento("Sal mineralizada", 0.05));
        suplementos.agregar(new Suplemento("Heno", 0.20));
        suplementos.agregar(new Suplemento("Concentrado", 0.30));

        JPanel p = new JPanel(new GridLayout(0, 2, 5, 5));
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // rotar mueve la lista circular, recarga K y pasto inicial y vuelve a simular
        p.add(lblPotrero);
        p.add(boton("Rotar potrero ⟳", e -> { potreros.avanzar(); cargarPotrero(); simular(); }));

        p.add(new JLabel("r crecimiento pasto")); p.add(tfR);
        p.add(new JLabel("K capacidad (kg/ha)")); p.add(tfK);
        p.add(new JLabel("m mortalidad/venta")); p.add(tfM);
        p.add(new JLabel("Pasto inicial")); p.add(tfP0);
        p.add(new JLabel("Ganado inicial")); p.add(tfG0);
        p.add(new JLabel("Días a simular")); p.add(tfDias);
        p.add(new JLabel("Tipo de ganado")); p.add(cbGanado);
        p.add(new JLabel("Tipo de pasto")); p.add(cbPasto);

        p.add(new JLabel("Suplemento:")); p.add(lblSuplemento);
        p.add(boton("◀ Anterior", e -> { suplementos.anterior(); mostrarSuplemento(); }));
        p.add(boton("Siguiente ▶", e -> { suplementos.siguiente(); mostrarSuplemento(); }));

        p.add(new JLabel("Evento")); p.add(cbEvento);
        p.add(new JLabel("Día de inicio")); p.add(tfDiaEv);
        p.add(new JLabel("Duración (días)")); p.add(tfDurEv);
        p.add(boton("Encolar evento", e -> encolarEvento()));
        p.add(boton("Vaciar cola", e -> { while (eventos.tam > 0) eventos.desencolar(); simular(); }));

        p.add(boton("Guardar escenario", e -> guardarEscenario()));
        p.add(boton("Deshacer", e -> deshacerEscenario()));

        JButton btnSimular = boton("▶  SIMULAR", e -> simular());
        btnSimular.setFont(btnSimular.getFont().deriveFont(Font.BOLD, 16f));
        btnSimular.setBackground(new Color(34, 139, 84));
        btnSimular.setForeground(Color.WHITE);

        JPanel izquierda = new JPanel(new BorderLayout());
        izquierda.add(p, BorderLayout.CENTER);
        izquierda.add(btnSimular, BorderLayout.SOUTH);
        izquierda.setPreferredSize(new Dimension(360, 700));

        resultados.setEditable(false);
        resultados.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(resultados);
        scroll.setPreferredSize(new Dimension(700, 230));

        setLayout(new BorderLayout(8, 8));
        add(izquierda, BorderLayout.WEST);
        add(grafica, BorderLayout.CENTER);
        add(scroll, BorderLayout.SOUTH);

        cargarPotrero();
        mostrarSuplemento();
        simular();

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);
    }

    JButton boton(String texto, java.awt.event.ActionListener accion) {
        JButton b = new JButton(texto);
        b.addActionListener(accion);
        return b;
    }

    // toma el potrero donde esta parado el cursor de la lista circular
    void cargarPotrero() {
        Potrero po = potreros.actual.dato;
        lblPotrero.setText(po.nombre());
        tfK.setText(String.valueOf(po.k()));
        tfP0.setText(String.valueOf(po.pasto0()));
    }

    // lo mismo con el cursor de la lista doble
    void mostrarSuplemento() {
        Suplemento s = suplementos.actual.dato;
        lblSuplemento.setText(s.nombre() + " (" + (int) (s.efecto() * 100) + "%)");
    }

    // la cola es FIFO, asi que los eventos tienen que llegar en orden de fecha.
    // si uno nuevo empieza antes de que termine el anterior lo rechazo, si no se corre en silencio
    void encolarEvento() {
        try {
            int dia = Integer.parseInt(tfDiaEv.getText().trim());
            int dur = Integer.parseInt(tfDurEv.getText().trim());
            if (dia < 0 || dur < 1) {
                JOptionPane.showMessageDialog(this, "El día no puede ser negativo y la duración mínima es 1.");
                return;
            }
            if (eventos.fin != null) {
                Evento ult = eventos.fin.dato;
                int libre = ult.dia() + ult.duracion();
                if (dia < libre) {
                    JOptionPane.showMessageDialog(this, "El evento anterior termina el día " + libre
                            + ". Programa este desde ese día.");
                    return;
                }
            }
            int tipo = cbEvento.getSelectedIndex();
            // 0 sequia, 1 invierno, 2 lluvias. menor que 1 frena el pasto, mayor que 1 lo ayuda
            double factor = (tipo == 0) ? 0.3 : (tipo == 1) ? 0.5 : 1.6;
            eventos.encolar(new Evento((String) cbEvento.getSelectedItem(), dia, dur, factor));
            simular();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Día y duración deben ser números enteros.");
        }
    }

    // apila los parametros mas la posicion del potrero y del suplemento
    void guardarEscenario() {
        try {
            escenarios.apilar(new Escenario(Double.parseDouble(tfR.getText()), Double.parseDouble(tfK.getText()),
                    Double.parseDouble(tfM.getText()), Double.parseDouble(tfP0.getText()),
                    Double.parseDouble(tfG0.getText()), cbGanado.getSelectedIndex(), cbPasto.getSelectedIndex(),
                    potreros.pos, suplementos.pos));
            simular();   // refresca el contador de la pila
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Revisa que los campos sean números.");
        }
    }

    // desapila y deja todo como estaba. primero muevo los cursores de las listas,
    // y cargarPotrero va antes de los setText porque pisa K y pasto inicial
    void deshacerEscenario() {
        Escenario e = escenarios.desapilar();
        if (e == null) { JOptionPane.showMessageDialog(this, "No hay escenarios guardados."); return; }
        while (potreros.pos != e.pot()) potreros.avanzar();
        while (suplementos.pos > e.sup()) suplementos.anterior();
        while (suplementos.pos < e.sup()) suplementos.siguiente();
        cargarPotrero();
        mostrarSuplemento();
        tfR.setText(String.valueOf(e.r()));   tfK.setText(String.valueOf(e.k()));
        tfM.setText(String.valueOf(e.m()));   tfP0.setText(String.valueOf(e.p0()));
        tfG0.setText(String.valueOf(e.g0()));
        cbGanado.setSelectedIndex(e.tg());    cbPasto.setSelectedIndex(e.tp());
        simular();
    }

    // nucleo matematico: Lotka-Volterra con pasto logistico, resuelto con Euler (h = 1 dia)
    //   dP/dt = r*P*(1 - P/K) - a*P*G
    //   dG/dt = b*P*G - m*G
    void simular() {
        double r, K, m, P, G;
        int dias;
        try {
            r = Double.parseDouble(tfR.getText());
            K = Double.parseDouble(tfK.getText());
            m = Double.parseDouble(tfM.getText());
            P = Double.parseDouble(tfP0.getText());
            G = Double.parseDouble(tfG0.getText());
            dias = Integer.parseInt(tfDias.getText().trim());
            if (K <= 0 || dias < 1 || dias > 5000 || r < 0 || m < 0 || P < 0 || G < 0)
                throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Revisa los datos: K > 0, días entre 1 y 5000 y nada negativo.");
            return;
        }

        int tg = cbGanado.getSelectedIndex(), tp = cbPasto.getSelectedIndex();
        // a y b salen de las matrices; el suplemento le baja el consumo a cada animal
        double a = CONSUMO[tg][tp] * (1 - suplementos.actual.dato.efecto());
        double b = CONVERSION[tg][tp];
        umbral = 0.25 * K;   // debajo de esto cuenta como sobrepastoreo

        pasto = new double[dias + 1];   // indice = dia, la posicion 0 es el estado inicial
        ganado = new double[dias + 1];
        pasto[0] = P;
        ganado[0] = G;
        censos = new ListaSimple<>();
        bandas = new ListaSimple<>();
        censos.agregar(new Censo(0, P, G));

        Cola<Evento> pendientes = eventos.copiar();
        Evento activo = null;
        int inicio = 0, fin = 0, diaSobre = -1;
        StringBuilder bitacora = new StringBuilder();

        for (int dia = 0; dia < dias; dia++) {
            // si el evento activo ya cumplio su duracion lo cierro y guardo su banda
            if (activo != null && dia >= fin) {
                bandas.agregar(new int[]{inicio, fin, activo.factorR() < 1 ? 1 : 0});
                bitacora.append("Día ").append(dia).append(": termina ").append(activo.nombre()).append('\n');
                activo = null;
            }
            // sin evento activo, si ya le toca al del frente de la cola lo saco
            if (activo == null && pendientes.tam > 0 && dia >= pendientes.frente.dato.dia()) {
                activo = pendientes.desencolar();
                inicio = dia;
                fin = dia + activo.duracion();
                bitacora.append("Día ").append(dia).append(": inicia ").append(activo.nombre()).append('\n');
            }
            double rDia = r * (activo == null ? 1 : activo.factorR());   // r afectada por el evento

            // Euler: valor nuevo = valor viejo + derivada * h
            double dP = rDia * P * (1 - P / K) - a * P * G;
            double dG = b * P * G - m * G;
            // piso en 0 para que Euler no deje poblaciones negativas
            P = Math.max(0, P + dP);
            G = Math.max(0, G + dG);
            pasto[dia + 1] = P;
            ganado[dia + 1] = G;

            // cada 30 dias va un censo a la lista simple
            if ((dia + 1) % 30 == 0) censos.agregar(new Censo(dia + 1, P, G));

            // solo me interesa el primer dia que cruza el umbral
            if (diaSobre < 0 && P < umbral) {
                diaSobre = dia + 1;
                bitacora.append("Día ").append(dia + 1).append(": ¡SOBREPASTOREO!\n");
            }
        }
        // si el evento sigue activo al terminar, cierro la banda en el ultimo dia
        if (activo != null) bandas.agregar(new int[]{inicio, Math.min(fin, dias), activo.factorR() < 1 ? 1 : 0});

        // equilibrio: dG/dt = 0 da P* = m/b, y con eso dP/dt = 0 da G*
        double pEq = m / b;
        double gEq = (pEq < K) ? r * (1 - pEq / K) / a : 0;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Equilibrio teórico: P* = %.0f kg/ha | carga sostenible G* = %.1f animales%n", pEq, gEq));
        sb.append(String.format("Final (día %d): pasto = %.0f kg/ha | ganado = %.1f animales%n", dias, P, G));
        sb.append(diaSobre >= 0 ? "ALERTA: sobrepastoreo desde el día " + diaSobre + "\n" : "OK: no hubo sobrepastoreo.\n");
        sb.append("\n--- Estructuras ---\n");
        sb.append("Lista circular: ").append(potreros.actual.dato.nombre()).append('\n');
        sb.append("Lista doble:    ").append(suplementos.actual.dato.nombre())
          .append(" (").append(suplementos.pos + 1).append("/").append(suplementos.tam).append(")\n");
        sb.append("Pila:           ").append(escenarios.tam).append(" escenarios guardados\n");
        sb.append("Cola:           ").append(eventos.tam).append(" eventos programados\n");
        sb.append("\n--- Bitácora ---\n").append(bitacora.length() == 0 ? "(sin eventos)\n" : bitacora.toString());
        sb.append("\n--- Censos (lista simple) ---\n");
        // recorro la lista simple de censos
        for (Nodo<Censo> x = censos.cabeza; x != null; x = x.sig)
            sb.append(String.format("Día %-4d pasto %-8.0f ganado %.1f%n", x.dato.dia(), x.dato.pasto(), x.dato.ganado()));
        resultados.setText(sb.toString());
        resultados.setCaretPosition(0);

        grafica.repaint();
    }

    // cada curva tiene su propia escala vertical, por eso hay maxP y maxG
    class Grafica extends JPanel {
        Grafica() { setBackground(Color.WHITE); }

        double maximo(double[] v) {
            double mx = 1;
            for (double x : v) mx = Math.max(mx, x);
            return mx;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            if (pasto == null) return;
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int ml = 20, mt = 55, mb = 35;
            int w = getWidth() - 2 * ml, h = getHeight() - mt - mb;
            int n = pasto.length;
            double maxP = Math.max(maximo(pasto), umbral) * 1.1;
            double maxG = maximo(ganado) * 1.1;

            // franjas de eventos: rojo los adversos, azul los favorables
            for (Nodo<int[]> x = bandas.cabeza; x != null; x = x.sig) {
                int x1 = ml + w * x.dato[0] / (n - 1), x2 = ml + w * x.dato[1] / (n - 1);
                g.setColor(x.dato[2] == 1 ? new Color(230, 80, 70, 50) : new Color(70, 130, 230, 50));
                g.fillRect(x1, mt, Math.max(x2 - x1, 1), h);
            }
            g.setColor(Color.GRAY);
            g.drawRect(ml, mt, w, h);

            // linea del umbral
            int yU = mt + h - (int) (h * umbral / maxP);
            g.setColor(Color.RED);
            g.drawLine(ml, yU, ml + w, yU);
            g.drawString("Umbral de sobrepastoreo", ml + 5, yU - 4);

            // une cada punto del vector con el siguiente
            g.setStroke(new BasicStroke(2.5f));
            for (int i = 1; i < n; i++) {
                int x1 = ml + w * (i - 1) / (n - 1), x2 = ml + w * i / (n - 1);
                g.setColor(new Color(34, 139, 84));
                g.drawLine(x1, mt + h - (int) (h * pasto[i - 1] / maxP), x2, mt + h - (int) (h * pasto[i] / maxP));
                g.setColor(new Color(214, 106, 28));
                g.drawLine(x1, mt + h - (int) (h * ganado[i - 1] / maxG), x2, mt + h - (int) (h * ganado[i] / maxG));
            }

            g.setColor(Color.BLACK);
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            g.drawString("Pasto vs Ganado (Lotka-Volterra)", ml, 22);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.setColor(new Color(34, 139, 84));
            g.drawString(String.format("● Pasto (máx %.0f kg/ha)", maximo(pasto)), ml, 42);
            g.setColor(new Color(214, 106, 28));
            g.drawString(String.format("● Ganado (máx %.1f animales)", maximo(ganado)), ml + 210, 42);
            g.setColor(Color.DARK_GRAY);
            g.drawString("Días: 0 → " + (n - 1), ml + w / 2 - 30, mt + h + 22);
        }
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel"); } catch (Exception e) { }
        SwingUtilities.invokeLater(() -> new PasturaGanaderia().setVisible(true));
    }
}
