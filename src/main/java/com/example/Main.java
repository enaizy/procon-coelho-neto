package com.example;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

// Classe principal do sistema do PROCON de Coelho Neto.
// Aqui fica o menu que o funcionário usa no dia a dia.
public class Main {
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Abre a conexão com o banco uma única vez e mantém aberta enquanto o menu estiver rodando
        try (Connection conexao = Conexao.conectar()) {
            System.out.println("Conexão com o banco realizada com sucesso!");

            int opcao;
            do {
                System.out.println("\n===== PROCON Coelho Neto - MVP =====");
                System.out.println("1 - Cadastrar cidadão");
                System.out.println("2 - Criar agendamento");
                System.out.println("3 - Buscar agendamentos por CPF");
                System.out.println("4 - Editar agendamento");
                System.out.println("5 - Cancelar agendamento");
                System.out.println("0 - Sair");
                System.out.print("Escolha uma opção: ");
                opcao = Integer.parseInt(scanner.nextLine());

                switch (opcao) {
                    case 1 -> cadastrarCidadao(scanner, conexao);
                    case 2 -> criarAgendamento(scanner, conexao);
                    case 3 -> buscarAgendamentos(scanner, conexao);
                    case 4 -> editarAgendamento(scanner, conexao);
                    case 5 -> cancelarAgendamento(scanner, conexao);
                    case 0 -> System.out.println("Encerrando o sistema...");
                    default -> System.out.println("Opção inválida.");
                }
            } while (opcao != 0);

        } catch (Exception e) {
            System.out.println("Erro: " + e.getMessage());
        }
    }

    // Remove pontos, traços e espaços do CPF, já que o cidadão pode digitar com ou sem formatação
    // (ex: "123.456.789-00" ou "12345678900" — os dois viram só números)
    private static String limparCpf(String cpf) {
        return cpf.replaceAll("[^0-9]", "");
    }

    // RF01: cadastro de cidadão, com validação básica de nome e CPF (RF06)
    private static void cadastrarCidadao(Scanner scanner, Connection conexao) {
        System.out.print("Nome: ");
        String nome = scanner.nextLine();
        System.out.print("CPF: ");
        String cpf = limparCpf(scanner.nextLine());
        System.out.print("Contato: ");
        String contato = scanner.nextLine();

        if (nome.isBlank() || cpf.isBlank()) {
            System.out.println("Nome e CPF são obrigatórios.");
            return;
        }
        if (!cpf.matches("\\d{11}")) {
            System.out.println("CPF inválido. Digite os 11 números do CPF.");
            return;
        }

        String sql = "INSERT INTO cidadao (nome, cpf, contato) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, nome);
            stmt.setString(2, cpf);
            stmt.setString(3, contato);
            stmt.executeUpdate();
            System.out.println("Cidadão cadastrado com sucesso!");
        } catch (SQLException e) {
            // Código "23505" é o padrão do PostgreSQL para violação de valor único (CPF duplicado)
            if ("23505".equals(e.getSQLState())) {
                System.out.println("Já existe um cidadão cadastrado com esse CPF.");
            } else {
                System.out.println("Erro ao cadastrar: " + e.getMessage());
            }
        }
    }

    // RF02: criação de agendamento vinculado a um cidadão já cadastrado
    private static void criarAgendamento(Scanner scanner, Connection conexao) {
        System.out.print("CPF do cidadão: ");
        String cpf = limparCpf(scanner.nextLine());

        // Primeiro busca o cidadão pelo CPF, para pegar o id dele e vincular ao age1ndamento
        int idCidadao = -1;
        String sqlBusca = "SELECT id_cidadao FROM cidadao WHERE cpf = ?";
        try (PreparedStatement stmt = conexao.prepareStatement(sqlBusca)) {
            stmt.setString(1, cpf);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                idCidadao = rs.getInt("id_cidadao");
            } else {
                System.out.println("Cidadão não encontrado. Cadastre-o primeiro (opção 1).");
                return;
            }
        } catch (SQLException e) {
            System.out.println("Erro ao buscar cidadão: " + e.getMessage());
            return;
        }

        System.out.print("Data do atendimento (DD/MM/AAAA): ");
        String dataTexto = scanner.nextLine();
        LocalDate data;
        try {
            data = LocalDate.parse(dataTexto, FORMATO_DATA);
        } catch (Exception e) {
            System.out.println("Data inválida. Use o formato DD/MM/AAAA.");
            return;
        }
        // Regra de negócio (RF06): não deixa agendar em uma data que já passou
        if (data.isBefore(LocalDate.now())) {
            System.out.println("Não é possível agendar em uma data passada.");
            return;
        }

        System.out.print("Horário (HH:MM): ");
        String hora = scanner.nextLine();

        String sql = "INSERT INTO agendamento (data_atendimento, hora_atendimento, id_cidadao) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setDate(1, java.sql.Date.valueOf(data));
            stmt.setTime(2, java.sql.Time.valueOf(hora + ":00"));
            stmt.setInt(3, idCidadao);
            stmt.executeUpdate();
            System.out.println("Agendamento criado com sucesso!");
        } catch (SQLException e) {
            System.out.println("Erro ao agendar: " + e.getMessage());
        }
    }

    // RF03: busca de agendamentos de um cidadão pelo CPF
    private static void buscarAgendamentos(Scanner scanner, Connection conexao) {
        System.out.print("CPF do cidadão: ");
        String cpf = limparCpf(scanner.nextLine());

        String sql = "SELECT a.id_agendamento, a.data_atendimento, a.hora_atendimento, a.status, c.nome " +
                     "FROM agendamento a JOIN cidadao c ON a.id_cidadao = c.id_cidadao " +
                     "WHERE c.cpf = ?";
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, cpf);
            ResultSet rs = stmt.executeQuery();
            boolean encontrou = false;
            while (rs.next()) {
                encontrou = true;
                System.out.println("ID: " + rs.getInt("id_agendamento") +
                    " | Cidadão: " + rs.getString("nome") +
                    " | Data: " + rs.getDate("data_atendimento").toLocalDate().format(FORMATO_DATA) +
                    " | Hora: " + rs.getTime("hora_atendimento") +
                    " | Status: " + rs.getString("status"));
            }
            if (!encontrou) {
                System.out.println("Nenhum agendamento encontrado para esse CPF.");
            }
        } catch (SQLException e) {
            System.out.println("Erro ao buscar: " + e.getMessage());
        }
    }

    // RF04: edição da data e horário de um agendamento já existente
    private static void editarAgendamento(Scanner scanner, Connection conexao) {
        System.out.print("ID do agendamento: ");
        int id = Integer.parseInt(scanner.nextLine());

        System.out.print("Nova data (DD/MM/AAAA): ");
        String dataTexto = scanner.nextLine();
        LocalDate data;
        try {
            data = LocalDate.parse(dataTexto, FORMATO_DATA);
        } catch (Exception e) {
            System.out.println("Data inválida. Use o formato DD/MM/AAAA.");
            return;
        }
        if (data.isBefore(LocalDate.now())) {
            System.out.println("Não é possível agendar em uma data passada.");
            return;
        }

        System.out.print("Novo horário (HH:MM): ");
        String hora = scanner.nextLine();

        String sql = "UPDATE agendamento SET data_atendimento = ?, hora_atendimento = ? WHERE id_agendamento = ?";
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setDate(1, java.sql.Date.valueOf(data));
            stmt.setTime(2, java.sql.Time.valueOf(hora + ":00"));
            stmt.setInt(3, id);
            int linhas = stmt.executeUpdate();
            System.out.println(linhas > 0 ? "Agendamento atualizado com sucesso!" : "Nenhum agendamento encontrado com esse ID.");
        } catch (SQLException e) {
            System.out.println("Erro ao atualizar: " + e.getMessage());
        }
    }

    // RF05: cancelamento de agendamento com exclusão lógica (o registro não é apagado do banco,
    // só muda o status para "Cancelado" — isso preserva o histórico para auditoria, como definido na Nota 1)
    private static void cancelarAgendamento(Scanner scanner, Connection conexao) {
        System.out.print("ID do agendamento a cancelar: ");
        int id = Integer.parseInt(scanner.nextLine());

        String sql = "UPDATE agendamento SET status = 'Cancelado' WHERE id_agendamento = ?";
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setInt(1, id);
            int linhas = stmt.executeUpdate();
            System.out.println(linhas > 0 ? "Agendamento cancelado com sucesso!" : "Nenhum agendamento encontrado com esse ID.");
        } catch (SQLException e) {
            System.out.println("Erro ao cancelar: " + e.getMessage());
        }
    }
}