require 'securerandom'

java_import Java::ee.ria.xroad.common.SystemProperties

class ImportController < ApplicationController

  IMPORTED_FILE_NAME = "xtee55_clients_importer_last"

  before_filter :verify_get, :only => [:get_imported, :last_result]
  before_filter :verify_post, :only => [:import_v5_data]

  upload_callbacks({
    :import_v5_data => "XROAD_IMPORT.uploadCallback"
  })

  def index
    authorize!(:execute_v5_import)
  end

  # -- Specific GET methods - start ---

  def get_imported
    file = V5Import.read()
    is_anything_imported = file != nil
    file_name = is_anything_imported ? file.file_name : nil

    translated_file_info = is_anything_imported ?
      t("import.file",
        :file => file_name,
        :time => format_time(file.created_at.localtime())) :
      t("import.no_file")

    result = {
      :file_type => t("import.label"),
      :file_info => translated_file_info,
      :file_name => file_name
    }

    render_json(result)
  end

  def last_result
    authorize!(:execute_v5_import)

    file = V5Import.read()
    console_output = file == nil ? [] : file.console_output.split("\n")

    render_json(:console => console_output)
  end

  # -- Specific GET methods - end ---

  def import_v5_data
    authorize!(:execute_v5_import)

    validate_params({
      :file_upload => [:required]
    })

    CommonUi::UploadedFile::Validator.new(
        params[:file_upload],
        GzipFile::Validator.new,
        GzipFile::restrictions).validate()

    data_file = write_imported_file(params[:file_upload])

    exit_status = execute_clients_importer(
        data_file, params[:file_upload].original_filename)

    V5DataImportStatus.write(data_file, exit_status)

    import_log_path = get_last_attempt_log_path

    case exit_status
    when 0
      notice(t("import.execute.success"))
    when 1
      notice(t("import.execute.warnings", :log_path => import_log_path))
    when 2
      raise t("import.execute.failure", :log_path => import_log_path)
    else
      raise t("import.execute.other_error")
    end

    render_json
  end

  private

  def write_imported_file(file)
    file_name = get_v5_import_file()
    CommonUi::IOUtils.write_binary(file_name, file.read)
    logger.debug("Importable data file saved to '#{file_name}'")

    return file_name
  end

  def get_v5_import_file()
    return "#{SystemProperties.getV5ImportPath()}/#{IMPORTED_FILE_NAME}"
  end

  def get_last_attempt_log_path()
    return "#{SystemProperties::getLogPath()}/xtee55_clients_importer-LAST.log"
  end

  def execute_clients_importer(data_file, original_filename)
    db_config = Rails.configuration.database_configuration[Rails.env]

    adapter = db_config["adapter"]
    database = db_config["database"]
    user = db_config["username"]
    pass = db_config["password"]

    commandline = ["/usr/share/xroad/bin/xtee55_clients_importer",
        "-d", data_file, "-t", adapter, "-b", database, "-u", user, "-p", pass]
    logger.debug("Executing V5 clients import")

    console_output_lines = CommonUi::ScriptUtils.run_script(commandline)
    V5Import.write(original_filename, console_output_lines)

    exit_status = $?.exitstatus
    logger.debug("Import exit status: #{exit_status}")

    return exit_status
  rescue RubyExecutableException => e
    V5Import.write(original_filename, e.console_output_lines)
    return 2 # Error
  end

  def write_last_attempt(console_output_lines)
    CommonUi::IOUtils.write(
        get_last_attempt_log_path(), console_output_lines.join("\n"))
  end
end
