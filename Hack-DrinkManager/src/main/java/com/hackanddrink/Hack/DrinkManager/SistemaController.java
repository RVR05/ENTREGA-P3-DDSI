package com.hackanddrink.Hack.DrinkManager;

import java.security.Timestamp;
import java.sql.*;
import javax.sql.DataSource;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;

@Controller
public class SistemaController {

    private final DataSource dataSource;

    public SistemaController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    //Este método captura AUTOMÁTICAMENTE cualquier SQLException lanzada por los métodos de abajo, me permite
    //obviar el try catch en estos porque si no tendríamos que poner la captura de la excepción cada vez
    //que trabajemos con la base de datos y es un pelín peñazo. Utiliza SQLException exactamente igual que con los
    //try-catch
    @ExceptionHandler(SQLException.class)
    @ResponseBody
    public String manejarErroresSQL(SQLException e) {
        return "ERROR EN LA BASE DE DATOS: " + e.getMessage() + " (Código: " + e.getErrorCode() + ")";
    }

    //A PARTIR DE AQUÍ COMIENZAN LA IMPLEMENTACIÓN DE LOS MÉTODOS REFERENTES A LAS OPERACIONES DEL SISTEMA

    //----OPERACIONES DE SERVICIOS----

    @GetMapping("/admin/servicios")
    public String opcionesServicios(){
        return "servicios-opciones";
    }

    @GetMapping("/admin/servicios/proveedores")
    public String asignarProveedores() {
        return "gestionar-proveedores";
    }

    //Esta es la función que asigna un proveedor a un servicio
    //Dado que tenía que enviar un listado, he decidio que maximo haya 10 entradas
    //en el HTML y que además se use un DTO (Una clase para recoger los datos del formulario)
    //porque no tenía muchas más opciones sin javascript y quería que tampoco fuera poner 800 paramrequest
    //el DTO ha sido declarado un poco más abajo, sé no es muy ortodoxo pero es para tenerlo todo en
    //un mismo archivo y que sea más fácil de ver, auqne también podría ser más lioso de ver con demasiado código
    //si crees que faltan muchas comprobaciones, como tamaños maximos... Esto es porque las contemplo en el html
    //de forma que el html no permite enviar datos que no cumplan esas restricciones
    //soy consciente de que el html puede ser modificado por el usuario, pero para este caso de uso
    //no me preocupa demasiado, ya que es un sistema interno y no público y además las comprobaciones más importantes
    //se hacen en la base de datos (como que no existan duplicados, que los datos existan previamente, etc)
    @PostMapping("/admin/servicios/proveedores-asignar" )
    public String asignarProveedores(@ModelAttribute FormularioAsignacionDTO formulario, Model model) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false); //la lógica transaccional es manejada por mí mismo
            //asegurandome de que no se producirán commits automáticos

            String sql = "SELECT COUNT(*) FROM PROVEEDORES WHERE NIF = ? ";
            try (PreparedStatement ps = conn.prepareStatement(sql)){

                ps.setString(1, formulario.getNifEmpresa());
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    int count = rs.getInt(1);
                    if (count == 0) {
                        model.addAttribute("mensaje", "El NIF de la empresa no existe en la base de datos.");
                        model.addAttribute("exito", false);
                        return "resultado-servicios";
                    }
                }
            }

            sql = "SELECT COUNT(*) FROM SERVICIOS WHERE NOMBRE = ? ";
            try(PreparedStatement ps = conn.prepareStatement(sql)){

                ps.setString(1, formulario.getNomServicio());
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    int count = rs.getInt(1);
                    if (count == 0) {
                        model.addAttribute("mensaje", "El nombre del servicio no existe en la base de datos.");
                        model.addAttribute("exito", false);
                        return "resultado-servicios";
                    }
                }
            }

            //lo he copiado de sqldeveloper y se ha puesto solo así, con los espacios y los saltos de linea
            //mejor así que todo en una sola linea.
            sql = "DECLARE\n" +
                    "\n" +
                    "nombre_relacion VARCHAR2(20);\n" +
                    "area_relacion NUMBER;\n" +
                    "conteo NUMBER;\n" +
                    "\n" +
                    "BEGIN\n" +
                    "\n" +
                    "nombre_relacion := ?;\n" +
                    "area_relacion := ?;\n" +
                    "\n" +
                    "SELECT COUNT(*) INTO conteo FROM Asignacion WHERE Nombre = nombre_relacion AND Area = area_relacion;\n" +
                    "\n" +
                    "? := conteo;\n" +
                    "\n" +
                    "IF conteo = 0 THEN\n" +
                    "INSERT INTO Asignacion VALUES ( nombre_relacion, area_relacion, ?, ?);\n" +
                    "END IF;\n" +
                    "\n" +
                    "END; ";

            try (CallableStatement ps = conn.prepareCall(sql)){

                ps.setString(1, formulario.getNomServicio());
                ps.setInt(2, formulario.getArea());
                ps.registerOutParameter(3, Types.INTEGER); // Parámetro de salida para el conteo
                ps.setString(4, formulario.getNifEmpresa());
                ps.setInt(5, formulario.getNumEmpleados());

                ps.execute();

                int count = ps.getInt(3); // Obtener el valor del conteo

                if (count > 0) {
                    conn.rollback();
                    model.addAttribute("mensaje", "Ya existe una asignación para este servicio en el área especificada.");
                    model.addAttribute("exito", false);
                    return "resultado-servicios";
                }
                if (formulario.getNumEmpleados() == 0){
                    conn.commit();
                } else{
                    int numEmpleados = formulario.getNumEmpleados() != null ? formulario.getNumEmpleados() : 0;

                    if (numEmpleados > 0 && formulario.getEmpleados() != null) {
                        //Si hay empleados, entramos en el bucle
                        //Saco un parametro para ver si el empleado ya existe
                        String sqlEmp = "DECLARE\n" +
                                "\n" +
                                "dni_relacion VARCHAR2(20);\n" +
                                "conteo NUMBER;\n" +
                                "conteo2 NUMBER;\n" +
                                "BEGIN\n" +
                                "\n" +
                                "dni_relacion := ?;\n" +
                                "\n" +
                                "SELECT COUNT(*) INTO conteo2 FROM Empleados WHERE DNI = dni_relacion;\n" +
                                "SELECT COUNT(*) INTO conteo  FROM Trabajo WHERE DNI = dni_relacion;\n" +
                                "\n" +
                                "? := conteo2;\n" +
                                "? := conteo;\n" +
                                "\n" +
                                "IF conteo = 0 AND conteo2 = 0 THEN\n" +
                                "INSERT INTO Empleados VALUES (dni_relacion, ?, ?, ?);\n" +
                                "INSERT INTO Trabajo VALUES ( dni_relacion, ?, ?);\n" +
                                "END IF;\n" +
                                "\n" +
                                "END;";

                        try (CallableStatement psEmp = conn.prepareCall(sqlEmp)) {
                            //Contador para asegurarnos de no procesar más de los indicados
                            int procesados = 0;

                            for (EmpleadoInputDTO empleado : formulario.getEmpleados()) {

                                //esto lo que hace es evitar que procese empleados vacios o nulos
                                if (empleado.getDni() == null || empleado.getDni().trim().isEmpty()) {
                                    continue; //Saltamos esta iteración si el DNI está vacío
                                }

                                // Si ya hemos procesado el número indicado de empleados, paramos
                                if (procesados >= numEmpleados) break;

                                psEmp.setString(1, empleado.getDni());
                                psEmp.registerOutParameter(2, Types.INTEGER); // conteo2(Existe empleado)
                                psEmp.registerOutParameter(3, Types.INTEGER); // conteo(Existe trabajo)
                                psEmp.setString(4, empleado.getNombre());
                                psEmp.setString(5, empleado.getApellidos());
                                psEmp.setString(6, empleado.getTelefono());
                                psEmp.setString(7, formulario.getNomServicio());
                                psEmp.setInt(8, formulario.getArea());

                                psEmp.execute();

                                int countEmp = psEmp.getInt(2);
                                int countTrabajo = psEmp.getInt(3);

                                if (countEmp > 0 || countTrabajo > 0) {
                                    conn.rollback(); //Importante hacer rollback si falla uno para que los demás no se queden guardados
                                    //recordemos es una transacción, aunque si directamente lanzamos un return se supone que no se hace commit
                                    model.addAttribute("mensaje", "El empleado con DNI " + empleado.getDni() + " ya existe en la base de datos o ya se le ha asignado un trabajo.");
                                    model.addAttribute("exito", false);
                                    return "resultado-servicios";
                                }
                                procesados++;
                            }
                            //Si el bucle termina sin errores
                            conn.commit();

                        }
                    }
                }
            }
        }

        model.addAttribute("mensaje", "Operación exitosa.");
        model.addAttribute("exito", true);
        return "resultado-servicios";
    }

    //este es el dto que usaré para recoger los datos del formulario de asignación de proveedores
    //este a su vez utiliza el dto de empleado que está más abajo para contener todos los datos
    //en una lista de tipo EmpleadoInputDTO
    public static class FormularioAsignacionDTO {
        private String nifEmpresa;
        private String nomServicio;
        private Integer numEmpleados;
        private Integer area;
        private java.util.List<EmpleadoInputDTO> empleados = new java.util.ArrayList<>();

        public String getNifEmpresa() {
            return nifEmpresa;
        }

        public void setNifEmpresa(String nifEmpresa) {
            this.nifEmpresa = nifEmpresa;
        }

        public String getNomServicio() {
            return nomServicio;
        }

        public void setNomServicio(String nomServicio) {
            this.nomServicio = nomServicio;
        }

        public Integer getNumEmpleados() {
            return numEmpleados;
        }

        public void setNumEmpleados(Integer numEmpleados) {
            this.numEmpleados = numEmpleados;
        }

        public java.util.List<EmpleadoInputDTO> getEmpleados() {
            return empleados;
        }

        public void setEmpleados(java.util.List<EmpleadoInputDTO> empleados) {
            this.empleados = empleados;
        }

        public Integer getArea() {
            return area;
        }

        public void setArea(Integer area) {
            this.area = area;
        }
    }

    //este es el dto que usaré para recoger los datos de cada empleado
    public static class EmpleadoInputDTO {
        private String nombre;
        private String apellidos;
        private String telefono;
        private String dni;

        public String getNombre() {
            return nombre;
        }

        public void setNombre(String nombre) {
            this.nombre = nombre;
        }

        public String getApellidos() {
            return apellidos;
        }

        public void setApellidos(String apellidos) {
            this.apellidos = apellidos;
        }

        public String getTelefono() {
            return telefono;
        }

        public void setTelefono(String telefono) {
            this.telefono = telefono;
        }

        public String getDni() {
            return dni;
        }

        public void setDni(String dni) {
            this.dni = dni;
        }
    }

    @GetMapping("/admin/servicios/turnos")
    public String asignarTurnos(){
        return "gestionar-turnos";
    }


    //los parámetros de fecha y hora se envían en formato ISO 8601 (YYYY-MM-DDTHH:MM:SS), es decir, en datime-local
    //Los capturamos omo LocalDateTime y luego los convertimos a Timestamp para pasarlos a Oracle, en Oracle uso DATE
    //porque maneja fecha y hora perfectamente con ese tipo de dato, timestamp es solaemnte para fracciones de segundo
    @PostMapping("/admin/servicios/turnos-crear")
    public String asignarTurnos(@RequestParam("dni") String dni, @RequestParam("area") int area,
                                @RequestParam("fechaInicio") @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime fechaInicio,
                                @RequestParam("fechaSalida") @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime fechaSalida,
                                Model model) throws SQLException{

        if (fechaInicio.isAfter(fechaSalida) || fechaInicio.isEqual(fechaSalida)) {
            model.addAttribute("mensaje", "Error: La fecha de inicio debe ser anterior a la de salida.");
            model.addAttribute("exito", false);
            return "resultado-servicios";
        }

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            //copiado y pegado desde sqldeveloper, por eso mantiene formato
            String sql = "DECLARE\n" +
                    "\n" +
                    "inicio DATE;\n" +
                    "salida DATE;\n" +
                    "v_dni VARCHAR2(9);\n" +
                    "area NUMBER;\n" +
                    "contador NUMBER;\n" +
                    "\n" +
                    "BEGIN\n" +
                    "\n" +
                    "inicio := ?;\n" +
                    "salida := ?;\n" +
                    "v_dni := ?;\n" +
                    "area := ?;\n" +
                    "\n" +
                    "SELECT COUNT(*) INTO contador FROM TURNOS WHERE DNI = v_dni AND (FechaInicio < salida AND FechaSalida > inicio);\n" +
                    "\n" +
                    "? := contador;\n" +
                    "\n" +
                    "IF contador = 0 THEN\n" +
                    "\n" +
                    "    INSERT INTO TURNOS VALUES (v_dni, inicio, salida, area);\n" +
                    "\n" +
                    "END IF;\n" +
                    "\n" +
                    "END;";

            try (CallableStatement cs = conn.prepareCall(sql)) {

                //Me ha estado dando errores si lo dejaba así  así que mejor convertirlo de
                //LocalDateTime a java.sql.Timestamp, que Oracle mapea a DATE/TIMESTAMP
                cs.setTimestamp(1, java.sql.Timestamp.valueOf(fechaInicio));
                cs.setTimestamp(2, java.sql.Timestamp.valueOf(fechaSalida));
                cs.setString(3, dni);
                cs.setInt(4, area);

                //Registramos el parámetro de salida para capturar el valor de 'contador'
                //que es el que nos permite saber si hay solapamientos para poder indicar al
                //usuario el tipo de error
                cs.registerOutParameter(5, Types.INTEGER);

                cs.execute();

                int solapamientos = cs.getInt(5);

                if (solapamientos > 0) {
                    conn.rollback(); //volvemos porque no se ha podido finalizar
                    model.addAttribute("mensaje", "Error: Se ha producido un solapamiento con la fecha de otro turno.");
                    model.addAttribute("exito", false);
                    return "resultado-servicios";
                } else {
                    conn.commit(); //commit exitoso
                    model.addAttribute("mensaje", "Turno creado correctamente para el DNI: " + dni);
                    model.addAttribute("exito", true);
                    return "resultado-servicios";
                }
            }

        }
    }


    @GetMapping("/admin/servicios/suministros")
    public String gestionarSuministros(){
        return "gestionar-suministros";
    }

    @PostMapping("/admin/servicios/suministros-reabastecer")
    public String reabastecerSuministros(@RequestParam("idSuministro") String idSuministro,
                                         @RequestParam("area") int area,
                                         @RequestParam("nivel") int nivel,
                                         Model model) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            String sql = "UPDATE SUMINISTROS SET Nivel = Nivel + ? WHERE Identificador = ? AND Area = ? ";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, nivel);
                ps.setString(2, idSuministro);
                ps.setInt(3, area);

                int filasActualizadas = ps.executeUpdate();

                if (filasActualizadas > 0) {

                    String sqlSelect = "SELECT Nivel FROM SUMINISTROS WHERE Identificador = ? AND Area = ?";
                    try (PreparedStatement psSelect = conn.prepareStatement(sqlSelect)) {
                        psSelect.setString(1, idSuministro);
                        psSelect.setInt(2, area);

                        ResultSet rs = psSelect.executeQuery();
                        int nuevoNivelTotal = 0;
                        if(rs.next()){
                            nuevoNivelTotal = rs.getInt(1);
                        }

                        conn.commit();

                        model.addAttribute("mensaje", "Reabastecimiento exitoso");
                        model.addAttribute("idSuministro", idSuministro);
                        model.addAttribute("area", area);
                        model.addAttribute("nivelTotal", nuevoNivelTotal);
                        model.addAttribute("exito", true);

                        return "resultados-suministros";
                    }
                } else {
                    conn.rollback();
                    model.addAttribute("mensaje", "Error: No se encontró el suministro con el ID y área especificados.");
                    model.addAttribute("exito", false);
                    return "resultados-suministros";
                }

            }
        }
    }

    @GetMapping("/admin/servicios/incidencia")
    public String reportarIncidencia(){
        return "gestionar-incidencia";
    }

    @PostMapping("/admin/servicios/incidencia-crear")
    public String reportarIncidencia(@RequestParam("dni") String dni,
                                     @RequestParam("comentario") String comentario,
                                     Model model) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            String sql = "SELECT COUNT(*) FROM CLIENTE WHERE DNI = ?";

            try(PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dni);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    int count = rs.getInt(1);
                    if (count == 0) {
                        //El DNI no existe en la tabla CLIENTES
                        conn.rollback();
                        model.addAttribute("mensaje", "No existe cliente con ese DNI registrado");
                        model.addAttribute("exito", false);
                        return "resultado-servicios";
                    }
                }
            }

            //genero un identificador aleatorio para la incidencia
            String caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            StringBuilder sb = new StringBuilder();
            java.util.Random random = new java.util.Random();
            for (int i = 0; i < 9; i++) {
                sb.append(caracteres.charAt(random.nextInt(caracteres.length())));
            }
            String identificador = sb.toString();

            //ejemplo de como las restricciones mas basicas de longitudes etc se pueden gestionar
            //para simplificar en el controlador no las he puesto todas, la mayoria esta en html
            if (comentario.length() > 200) {
                model.addAttribute("mensaje", "Comentario demasiado largo, máximo 200 caracteres.");
                model.addAttribute("exito", false);
                return "resultado-servicios";
            }
            sql = "INSERT INTO INCIDENCIA VALUES (?, ?, ?)";

            try(PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dni);
                ps.setString(2, identificador);
                ps.setString(3, comentario);

                ps.executeUpdate();
                conn.commit();

                model.addAttribute("mensaje", "Incidencia reportada correctamente con ID: " + identificador);
                model.addAttribute("exito", true);
                return "resultado-servicios";
            }

        }

    }

    @GetMapping("/admin/servicios/valoracion")
    public String enviarValoracion(){
        return "gestionar-valoracion";
    }

    @PostMapping("/admin/servicios/valoracion-enviar")
    public String procesarValoracion(@RequestParam("dni") String dni,
                                     @RequestParam("localizador") String localizador,
                                     @RequestParam("valoracion") int valoracion,
                                     @RequestParam("comentario") String comentario,
                                     Model model) throws SQLException {

        //quiero comprobar que no se mete valoración negativa o mayor que 5
        //quiero comprobar que la longitud de la descripción no es excesiva
        if (valoracion < 0 || valoracion > 5) {
            model.addAttribute("mensaje", "Error: La valoración debe estar entre 0 y 5.");
            model.addAttribute("exito", false);
            return "resultado-servicios";
        }

        if (comentario.length() > 200){
            model.addAttribute("mensaje", "Error: El comentario es demasiado largo (máximo 300 caracteres).");
            model.addAttribute("exito", false);
            return "resultado-servicios";
        }

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);


            String sql = "SELECT COUNT(*) FROM ENTRADA WHERE DNI = ? AND Localizador = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dni);
                ps.setString(2, localizador);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    int existe = rs.getInt(1);
                    if (existe == 0) {
                        conn.rollback();
                        model.addAttribute("mensaje", "Error: No se encontró una entrada válida para ese DNI y Localizador.");
                        model.addAttribute("exito", false);
                        return "resultado-servicios";
                    }
                }
            }

            String caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            StringBuilder sb = new StringBuilder();
            java.util.Random random = new java.util.Random();
            for (int i = 0; i < 9; i++) {
                sb.append(caracteres.charAt(random.nextInt(caracteres.length())));
            }
            String identificador = sb.toString();

            sql = "INSERT INTO VALORACION (DNI, Localizador, Identificador, Comentario, Valoracion) VALUES (?, ?, ?, ?, ?)";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dni);
                ps.setString(2, localizador);
                ps.setString(3, identificador);
                ps.setString(4, comentario);
                ps.setInt(5, valoracion);

                ps.executeUpdate();
                conn.commit();

                model.addAttribute("mensaje", "Valoración enviada correctamente. ID Referencia: " + identificador);
                model.addAttribute("exito", true);
                return "resultado-servicios";
            }
        }
    }


    //----FIN DE LAS OPERACIONES DE SERVICIOS----


}

