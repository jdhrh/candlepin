#! /usr/bin/env ruby
require 'net/http'
require 'webrick'
require 'webrick/https'

require 'rspec/autorun'
require '../candlepin'

RSpec.configure do |config|
  config.expect_with :rspec do |c|
    c.syntax = :expect
  end
end

RSpec::Matchers.define :be_2xx do |expected|
  match do |res|
    (200..206).include?(res.status_code)
  end
end

RSpec::Matchers.define :be_unauthorized do |expected|
  match do |res|
    res.status_code == 401
  end
end

RSpec::Matchers.define :be_forbidden do |expected|
  match do |res|
    res.status_code == 403
  end
end

RSpec::Matchers.define :be_missing do |expected|
  match do |res|
    res.status_code == 404
  end
end

module Candlepin
  describe "Candlepin" do
    def rand_string(opts = {})
      len = opts[:len] || 9
      prefix = opts[:prefix] || ''
      o = [('a'..'z'), ('A'..'Z'), ('1'..'9')].map { |range| range.to_a }.flatten
      rand = (0...len).map { o[rand(o.length)] }.join

      return (prefix.empty?) ? rand : "#{prefix}-#{rand}"
    end

    context "in a functional context", :functional => true do
      # The let! prevents lazy loading
      let!(:user_client) { BasicAuthClient.new }
      let!(:no_auth_client) { NoAuthClient.new }

      let(:owner) do
        key = rand_string(:prefix => 'owner')
        user_client.create_owner(
          :key => key,
          :display_name => key,
        ).content
      end

      let(:owner_user) do
        user_client.create_user_under_owner(
          :username => rand_string(:prefix => 'owner_user'),
          :password => rand_string,
          :key => owner[:key],
          :super_admin => false,
        )
      end

      let(:owner_admin) do
        user_client.create_user_under_owner(
          :username => rand_string(:prefix => 'owner_admin'),
          :password => rand_string,
          :key => owner[:key],
          :super_admin => trutrue,
        )
      end

      let(:role) do
        user_client.create_role(
          :name => rand_string(:prefix => 'role'),
        ).content
      end

      let(:content) do
        user_client.create_content(
          :content_id => "hello",
          :name => "Hello",
          :label => "hello",
          :key => owner[:key],
        ).content
      end

      let(:product) do
        p = rand_string(:prefix => 'product')
        user_client.create_product(
          :product_id => p,
          :name => "Product #{p}",
          :key => owner[:key],
        ).content
      end

      it 'gets a status as JSON' do
        res = no_auth_client.get('/status')
        expect(res.content.key?(:version)).to be_true
      end

      it 'gets all owners with basic auth' do
        user_client.create_owner(
          :key => rand_string,
          :display_name => rand_string,
        )
        res = user_client.get_all_owners
        expect(res.content.empty?).to be_false
        expect(res.content.first.key?(:id)).to be_true
      end

      it 'fails with bad password' do
        res = no_auth_client.get('/owners')
        expect(res).to be_unauthorized
      end

      it 'registers a consumer' do
        res = user_client.register(
          :owner => owner[:key],
          :username => owner_user[:username],
          :name => rand_string,
        )
        expect(res.content[:uuid].length).to eq(36)
      end

      it 'registers a consumer and gets a client' do
        x509_client = user_client.register_and_get_client(
          :owner => owner[:key],
          :username => owner[:key],
          :name => rand_string,
        )

        res = x509_client.get_consumer()
        expect(res.content[:uuid].length).to eq(36)
      end

      it 'binds to a pool ID' do
        user_client.create_subscription(
          :key => owner[:key],
          :product_id => product[:id],
        ).content

        pools = user_client.get_owner_pools(:key => owner[:key]).content
        expect(pools.first[:product][:id]).to eq(product[:id])

        x509_client = user_client.register_and_get_client(
          :owner => owner[:key],
          :name => rand_string,
        )

        res = x509_client.bind(:pool => pools.first[:id])
        expect(res).to be_2xx

        entitlement = res.content.first
        expect(entitlement[:certificates]).to_not be_empty
        expect(entitlement[:certificates].first.key?(:key)).to be_true
      end

      it 'binds to a product ID' do
        user_client.create_subscription(
          :key => owner[:key],
          :product_id => product[:id],
        )

        pools = user_client.get_owner_pools(:key => owner[:key]).content
        expect(pools.first[:product][:id]).to eq(product[:id])

        x509_client = user_client.register_and_get_client(
          :owner => owner[:key],
          :name => rand_string,
          :username => owner_user[:username],
          :installed_products => product,
        )

        res = x509_client.bind(:product => product[:id])
        expect(res).to be_2xx

        entitlement = res.content.first
        expect(entitlement[:certificates]).to_not be_empty
        expect(entitlement[:certificates].first.key?(:key)).to be_true
      end

      it 'gets deleted consumers' do
        res = user_client.get_deleted_consumers
        expect(res).to be_2xx
      end

      it 'updates a consumer' do
        res = user_client.register(
          :owner => owner[:key],
          :username => owner_user[:username],
          :name => rand_string,
        )
        consumer = res.content

        res = user_client.update_consumer(
          :autoheal => false,
          :uuid => consumer[:uuid],
          :capabilities => [:cores],
        )
        expect(res).to be_2xx
      end

      it 'allows a client to set a sticky uuid' do
        res = user_client.register(
          :owner => owner[:key],
          :username => owner_user[:username],
          :name => rand_string,
        )
        consumer = res.content
        user_client.uuid = consumer[:uuid]

        res = user_client.update_consumer(
          :autoheal => false,
        )
        expect(res).to be_2xx
      end

      it 'updates a consumer guest id list' do
        res = user_client.register(
          :owner => owner[:key],
          :username => owner_user[:username],
          :name => rand_string,
        )
        consumer = res.content
        user_client.uuid = consumer[:uuid]

        res = user_client.update_all_guest_ids(
          :guest_ids => ['123', '456'],
        )
        expect(res).to be_2xx
      end

      it 'deletes a guest id' do
        res = user_client.register(
          :owner => owner[:key],
          :username => owner_user[:username],
          :name => rand_string,
        )
        consumer = res.content
        user_client.uuid = consumer[:uuid]

        user_client.update_consumer(
          :guest_ids => ['x', 'y', 'z'],
        )
        expect(res).to be_2xx

        res = user_client.delete_guest_id(
          :guest_id => 'x',
        )
        expect(res).to be_2xx
      end

      it 'creates users' do
        res = user_client.create_user(
          :username => rand_string(:prefix => 'user'),
          :password => rand_string,
        )
        expect(res).to be_2xx
        user = res.content
        expect(user[:hashedPassword].length).to eq(40)
      end

      it 'creates users under owners' do
        name = rand_string
        password = rand_string
        user = user_client.create_user_under_owner(
          :username => name,
          :password => password,
          :key => owner[:key])
        expect(user[:username]).to eq(name)
        expect(user[:password]).to eq(password)

        res = user_client.get_user_roles(:username => user[:username])
        roles = res.content.map { |r| r[:name] }
        expect(roles).to include("#{owner[:key]}-ALL")
      end

      it 'creates multiple users under an owner' do
        name = rand_string
        password = rand_string
        user = user_client.create_user_under_owner(
          :username => name,
          :password => password,
          :key => owner[:key])
        expect(user[:username]).to eq(name)
        expect(user[:password]).to eq(password)

        name2 = rand_string
        password2 = rand_string
        user2 = user_client.create_user_under_owner(
          :username => name2,
          :password => password2,
          :key => owner[:key])
        expect(user2[:username]).to eq(name2)
        expect(user2[:password]).to eq(password2)

        [user[:username], user2[:username]].each do |n|
          res = user_client.get_user_roles(:username => n)
          roles = res.content.map { |r| r[:name] }
          expect(roles).to include("#{owner[:key]}-ALL")
        end
      end

      it 'resets a client to a given user' do
        name = rand_string
        password = rand_string
        user = user_client.create_user_under_owner(
          :username => name,
          :password => password,
          :key => owner[:key])

        expect(user_client.username).to eq('admin')
        user_client.switch_auth(user[:username], user[:password])

        expect(user_client.username).to eq(name)

        res = user_client.get_user_roles(:username => user[:username])
        roles = res.content.map { |r| r[:name] }
        expect(roles).to include("#{owner[:key]}-ALL")
      end

      it 'resets a client to a given user by passing in a user' do
        name = rand_string
        password = rand_string
        user = user_client.create_user_under_owner(
          :username => name,
          :password => password,
          :key => owner[:key])

        expect(user_client.username).to eq('admin')
        user_client.switch_auth(user)

        expect(user_client.username).to eq(name)

        res = user_client.get_user_roles(:username => user[:username])
        roles = res.content.map { |r| r[:name] }
        expect(roles).to include("#{owner[:key]}-ALL")
      end

      it 'gets users' do
        user = user_client.create_user(
          :username => rand_string(:prefix => 'user'),
          :password => rand_string,
        ).content

        res = user_client.get_user(:username => user[:username])
        expect(res.content[:id]).to eq(user[:id])
      end

      it 'updates users' do
        res = user_client.update_user(:username => owner_user[:username], :password => rand_string)
        expect(res.content[:hashedPassword]).to_not eq(owner_user[:hashedPassword])
      end

      it 'deletes users' do
        res = user_client.delete_user(:username => owner_user[:username])
        expect(res).to be_2xx

        res = user_client.get_all_users
        existing_users = res.content.map { |u| u[:username] }
        expect(existing_users).to_not include(owner_user[:username])
      end

      it 'creates roles' do
        res = user_client.create_role(
          :name => rand_string(:prefix => 'role'),
        )
        expect(res).to be_2xx

        expect(res.content[:id]).to_not be_nil
      end

      it 'gets roles' do
        res = user_client.get_role(
          :role_id => role[:id],
        )
        expect(res.content[:id]).to eq(role[:id])
      end

      it 'updates roles' do
        res = user_client.update_role(
          :role_id => role[:id],
          :name => rand_string,
        )
        expect(res.content[:name]).to_not eq(role[:name])
      end

      it 'deletes roles' do
        expect(role[:id]).to_not be_nil

        res = user_client.delete_role(
          :role_id => role[:id],
        )
        expect(res).to be_2xx
      end

      it 'creates role users' do
        user = user_client.create_user(
          :username => rand_string(:prefix => 'user'),
          :password => rand_string,
        ).content

        res = user_client.add_role_user(
          :role_id => role[:id],
          :username => user[:username],
        )
        expect(res.content[:users].first[:id]).to eq(user[:id])
      end

      it 'deletes role users' do
        user = user_client.create_user(
          :username => rand_string(:prefix => 'user'),
          :password => rand_string,
        ).content

        res = user_client.add_role_user(
          :role_id => role[:id],
          :username => user[:username],
        )
        expect(res.content[:users].first[:id]).to eq(user[:id])

        res = user_client.delete_role_user(
          :role_id => role[:id],
          :username => user[:username],
        )
        expect(res.content[:users]).to be_empty
      end

      it 'adds role permissions' do
        res = user_client.add_role_permission(
          :role_id => role[:id],
          :owner => owner[:key],
          :type => 'OWNER',
          :access => 'ALL',
        )
        expect(res).to be_2xx
      end

      it 'deletes role permissions' do
        perm = user_client.add_role_permission(
          :role_id => role[:id],
          :owner => owner[:key],
          :type => 'OWNER',
          :access => 'ALL',
        ).content

        res = user_client.delete_role_permission(
          :role_id => role[:id],
          :permission_id => perm[:permissions].first[:id],
        )
        expect(res).to be_2xx
      end

      it 'creates owners' do
        res = user_client.create_owner(
          :key => rand_string,
          :display_name => rand_string,
        )
        expect(res).to be_2xx
        expect(res.content).to have_key(:id)
      end

      it 'gets owner hypervisors' do
        host1 = user_client.register(
          :owner => owner[:key],
          :username => 'admin',
          :name => rand_string,
        ).content

        host2 = user_client.register(
          :owner => owner[:key],
          :username => 'admin',
          :name => rand_string,
        ).content

        user_client.register(
          :owner => owner[:key],
          :username => 'admin',
          :name => rand_string,
          :hypervisor_id => host1[:uuid],
        ).content

        res = user_client.get_owner_hypervisors(
          :key => owner[:key],
        )
        expect(res).to be_2xx

        res = user_client.get_owner_hypervisors(
          :key => owner[:key],
          :hypervisor_ids => [host1[:uuid], host2[:uuid]],
        )
        expect(res).to be_2xx
        expect(res.content.length).to eq(1)
      end

      it 'creates owner environments' do
        res = user_client.create_owner_environment(
          :key => owner[:key],
          :id => rand_string,
          :description => rand_string,
          :name => rand_string
        )
        expect(res).to be_2xx
        expect(res.content).to have_key(:name)
      end

      it 'gets owner environments' do
        env = user_client.create_owner_environment(
          :key => owner[:key],
          :id => rand_string,
          :description => rand_string,
          :name => rand_string
        ).content

        res = user_client.get_owner_environment(
          :key => owner[:key],
          :name => env[:name]
        )
        expect(res).to be_2xx
      end

      it 'deletes owners' do
        res = user_client.delete_owner(
          :key => owner[:key]
        )
        expect(res).to be_2xx

        res = user_client.get_owner(
          :key => owner[:key]
        )
        expect(res).to be_missing
      end

      it 'creates child owners' do
        parent = owner
        child = user_client.create_owner(
          :key => rand_string,
          :display_name => rand_string,
          :parent_owner => parent,
        ).content

        expect(child[:parentOwner][:id]).to eq(parent[:id])
        expect(parent[:parentOwner]).to be_nil
      end

      it 'updates an owner' do
        old_name = owner[:displayName]
        res = user_client.update_owner(
          :key => owner[:key],
          :display_name => rand_string
        )
        expect(res).to be_2xx
        expect(res.content[:displayName]).to_not eq(old_name)
      end

      it 'gets owner service levels' do
        res = user_client.get_owner_service_levels(
          :key => owner[:key],
          :exempt => true,
        )

        expect(res).to be_2xx
      end

      it 'sets owner log level' do
        res = user_client.set_owner_log_level(
          :key => owner[:key],
          :level => 'debug',
        )
        expect(res).to be_2xx
        expect(res.content[:logLevel]).to eq('DEBUG')
      end

      it 'deletes owner log level' do
        res = user_client.set_owner_log_level(
          :key => owner[:key],
          :level => 'debug',
        )
        expect(res).to be_2xx
        expect(res.content[:logLevel]).to eq('DEBUG')

        res = user_client.delete_owner_log_level(
          :key => owner[:key],
        )
        expect(res).to be_2xx
      end

      it 'gets owners' do
        res = user_client.get_owner(
          :key => owner[:key],
        )
        expect(res).to be_2xx
        expect(res.content[:id]).to eq(owner[:id])
      end

      it 'gets owner info' do
        res = user_client.get_owner_info(
          :key => owner[:key],
        )
        expect(res).to be_2xx
      end

      it "gets owner jobs" do
        res = user_client.get_owner_jobs(
          :owner => owner[:key],
        )
        expect(res).to be_2xx
      end

      it 'gets a crl' do
        crl = user_client.get_crl
        expect(crl).to be_kind_of(OpenSSL::X509::CRL)
      end

      it 'gets environments' do
        res = user_client.get_environments
        expect(res).to be_2xx
      end

      it 'creates an owner product' do
        res = user_client.create_product(
          :product_id => rand_string,
          :name => rand_string,
          :multiplier => 2,
          :attributes => { :arch => 'x86_64' },
          :key => owner[:key],
        )
        expect(res).to be_2xx
        expect(res.content[:multiplier]).to eq(2)
      end

      it 'deletes an owner product' do
        product = user_client.create_product(
          :product_id => rand_string,
          :name => rand_string,
          :multiplier => 2,
          :attributes => { :arch => 'x86_64' },
          :key => owner[:key],
        ).content

        res = user_client.delete_product(
          :product_id => product[:id],
          :key => owner[:key],
        )
        expect(res).to be_2xx

        res = user_client.get_owner_product(
          :product_id => product[:id],
          :key => owner[:key],
        )
        expect(res).to be_missing
      end

      it 'updates an owner product' do
        product = user_client.create_product(
          :product_id => rand_string,
          :name => rand_string,
          :multiplier => 2,
          :attributes => { :arch => 'x86_64' },
          :key => owner[:key],
        ).content

        res = user_client.update_product(
          :product_id => product[:id],
          :multiplier => 8,
          :key => owner[:key],
        )
        expect(res).to be_2xx

        res = user_client.get_owner_product(
          :product_id => product[:id],
          :key => owner[:key],
        )
        expect(res.content[:multiplier]).to eq(8)
      end

      it 'creates owner content' do
        res = user_client.create_content(
          :content_id => "hello",
          :name => "Hello",
          :label => "hello",
          :key => owner[:key],
        )

        expect(res).to be_2xx
      end

      it 'updates owner content' do
        product = user_client.create_product(
          :product_id => rand_string,
          :name => rand_string,
          :multiplier => 2,
          :attributes => { :arch => 'x86_64' },
          :key => owner[:key],
        ).content

        res = user_client.update_product_content(
          :product_id => product[:id],
          :content_id => content[:id],
          :key => owner[:key],
        )

        expect(res).to be_2xx
      end

      it 'deletes owner content' do
        product = user_client.create_product(
          :product_id => rand_string,
          :name => rand_string,
          :multiplier => 2,
          :attributes => { :arch => 'x86_64' },
          :key => owner[:key],
        ).content
        expect(product[:productContent]).to be_empty

        res = user_client.update_product_content(
          :product_id => product[:id],
          :content_id => content[:id],
          :key => owner[:key],
        )
        expect(res).to be_2xx

        product = user_client.get_product(
          :product_id => product[:id],
        ).content
        expect(product[:productContent]).to_not be_empty

        res = user_client.delete_product_content(
          :product_id => product[:id],
          :content_id => content[:id],
          :key => owner[:key],
        )
        expect(res).to be_2xx

        product = user_client.get_product(
          :product_id => product[:id],
        ).content
        expect(product[:productContent]).to be_empty
      end

      it 'creates subscriptions' do
        res = user_client.create_subscription(
          :key => owner[:key],
          :product_id => product[:id],
        )
        expect(res).to be_2xx
        expect(res.content[:product][:id]).to eq(product[:id])
      end

      it 'gets owners with pools of a product' do
        id1 = rand_string
        user_client.create_product(
          :product_id => id1,
          :name => rand_string,
          :multiplier => 2,
          :attributes => { :arch => 'x86_64' },
          :key => owner[:key],
        )

        user_client.create_subscription(
          :key => owner[:key],
          :product_id => id1,
        )

        results = user_client.get_owners_with_product(:product_ids => [id1]).content
        expect(results.first[:key]).to eq(owner[:key])
      end

      it 'creates a distributor version' do
        name = rand_string
        res = user_client.create_distributor_version(
          :name => name,
          :display_name => rand_string,
          :capabilities => [:ram],
        )
        expect(res).to be_2xx
        expect(res.content[:name]).to eq(name)
      end

      it 'deletes a distributor version' do
        distributor = user_client.create_distributor_version(
          :name => rand_string,
          :display_name => rand_string,
        ).content

        res = user_client.delete_distributor_version(
          :id => distributor[:id]
        )
        expect(res).to be_2xx
      end

      it 'updates a distributor version' do
        distributor = user_client.create_distributor_version(
          :name => rand_string,
          :display_name => rand_string,
          :capabilities => [:ram],
        ).content

        new_display_name = rand_string
        res = user_client.update_distributor_version(
          :id => distributor[:id],
          :display_name => new_display_name,
        )
        expect(res).to be_2xx

        res = user_client.get_distributor_version(
          :name => distributor[:name]
        )
        expect(res.content.first[:displayName]).to eq(new_display_name)
      end

      it 'creates a consumer type' do
        res = user_client.create_consumer_type(
          :label => rand_string
        )
        expect(res).to be_2xx
      end

      it 'deletes a consumer type' do
        type = user_client.create_consumer_type(
          :label => rand_string
        ).content

        res = user_client.delete_consumer_type(
          :type_id => type[:id]
        )
        expect(res).to be_2xx

        res = user_client.get_consumer_type(
          :type_id => type[:id]
        )
        expect(res).to be_missing
      end
    end

    context "in a unit test context", :unit => true do
      TEST_PORT = 11999
      CLIENT_CERT_TEST_PORT = TEST_PORT + 1
      attr_accessor :server
      attr_accessor :client_cert_server

      before(:all) do
        util_test_class = Class.new(Object) do
          include Util
        end
        Candlepin.const_set("UtilTest", util_test_class)
      end

      before(:each) do
        key = OpenSSL::PKey::RSA.new(File.read('certs/server.key'))
        cert = OpenSSL::X509::Certificate.new(File.read('certs/server.crt'))

        server_config = {
          :BindAddress => 'localhost',
          :Port => TEST_PORT,
          :SSLEnable => true,
          :SSLPrivateKey => key,
          :SSLCertificate => cert,
          :Logger => WEBrick::BasicLog.new(nil, WEBrick::BasicLog::FATAL),
          :AccessLog => [],
        }

        @server = WEBrick::HTTPServer.new(server_config)
        @client_cert_server = WEBrick::HTTPServer.new(server_config.merge({
          :SSLVerifyClient => OpenSSL::SSL::VERIFY_PEER | OpenSSL::SSL::VERIFY_FAIL_IF_NO_PEER_CERT,
          :SSLCACertificateFile => 'certs/test-ca.crt',
          :Port => CLIENT_CERT_TEST_PORT,
        }))

        [server, client_cert_server].each do |s|
          s.mount_proc('/candlepin/status') do |req, res|
            if req.accept.include?('text/plain')
              res.body = 'Hello Text'
              res['Content-Type'] = 'text/plain'
            elsif req.accept.include?('bad/type')
              res.body = 'ERROR'
              res['Content-Type'] = 'text/plain'
            else
              res.body = '{ "message": "Hello" }'
              res['Content-Type'] = 'text/json'
            end
          end
        end

        @server_thread = Thread.new do
          server.start
        end

        @client_cert_server_thread = Thread.new do
          client_cert_server.start
        end
      end

      after(:each) do
        server.shutdown unless server.nil?
        @server_thread.join unless @server_thread.nil?

        client_cert_server.shutdown unless client_cert_server.nil?
        @client_cert_server_thread.join unless @client_cert_server_thread.nil?
      end

      it 'uses CA if given' do
        simple_client = NoAuthClient.new(
          :ca_path => 'certs/test-ca.crt',
          :port => TEST_PORT,
          :insecure => false)

        res = simple_client.get('/status')
        expect(res.content[:message]).to eq("Hello")
      end

      it 'makes text/plain requests' do
        simple_client = NoAuthClient.new(
          :port => TEST_PORT)
        res = simple_client.get_text('/status')
        expect(res.content).to eq("Hello Text")
      end

      it 'uses an indifferent access hash' do
        simple_client = NoAuthClient.new(
          :ca_path => 'certs/test-ca.crt',
          :port => TEST_PORT,
          :insecure => false)

        res = simple_client.get('/status')
        expect(res.content[:message]).to eq("Hello")
        expect(res.content[:message]).to eq("Hello")
      end

      it 'allows arbitrary accept headers' do
        simple_client = NoAuthClient.new(
          :port => TEST_PORT)
        res = simple_client.get_type('bad/type', '/status')
        expect(res.content).to eq("ERROR")
      end

      it 'fails to connect if no CA given in strict mode' do
        simple_client = NoAuthClient.new(
          :port => TEST_PORT,
          :insecure => false)

        expect do
          simple_client.get('/status')
        end.to raise_error(OpenSSL::SSL::SSLError)
      end

      it 'allows a connection with a valid client cert' do
        client_cert = OpenSSL::X509::Certificate.new(File.read('certs/client.crt'))
        client_key = OpenSSL::PKey::RSA.new(File.read('certs/client.key'))
        cert_client = X509Client.new(
          :port => CLIENT_CERT_TEST_PORT,
          :ca_path => 'certs/test-ca.crt',
          :insecure => false,
          :client_cert => client_cert,
          :client_key => client_key)

        res = cert_client.get('/status')
        expect(res.content[:message]).to eq("Hello")
      end

      it 'forbids a connection with an invalid client cert' do
        client_cert = OpenSSL::X509::Certificate.new(File.read('certs/unsigned.crt'))
        client_key = OpenSSL::PKey::RSA.new(File.read('certs/unsigned.key'))
        cert_client = X509Client.new(
          :port => CLIENT_CERT_TEST_PORT,
          :ca_path => 'certs/test-ca.crt',
          :insecure => false,
          :client_cert => client_cert,
          :client_key => client_key)

        expect do
          cert_client.get('/status')
        end.to raise_error(OpenSSL::SSL::SSLError, /unknown ca/)
      end

      it 'builds a correct base url' do
        simple_client = NoAuthClient.new(
          :host => "www.example.com",
          :port => 8443,
          :context => "/some_path",
        )
        expect(simple_client.base_url).to eq("https://www.example.com:8443/some_path")
      end

      it 'handles a context with no leading slash' do
        simple_client = NoAuthClient.new(
          :host => "www.example.com",
          :port => 8443,
          :context => "no_slash_path",
        )
        expect(simple_client.base_url).to eq("https://www.example.com:8443/no_slash_path")
      end

      it 'reloads underlying client when necessary' do
        simple_client = NoAuthClient.new(
          :host => "www.example.com",
          :port => 8443,
          :context => "/1",
        )
        url1 = "https://www.example.com:8443/1"
        expect(simple_client.base_url).to eq(url1)
        expect(simple_client.raw_client.base_url).to eq(url1)
        expect(simple_client.raw_client).to be_kind_of(HTTPClient)

        simple_client.context = "/2"
        simple_client.reload

        url2 = "https://www.example.com:8443/2"
        expect(simple_client.base_url).to eq(url2)
        expect(simple_client.raw_client.base_url).to eq(url2)
      end

      it 'builds a client from consumer json' do
        # Note that the consumer.json file has had the signed client.crt and
        # client.key contents inserted into it.
        cert_client = X509Client.from_consumer(
          JSON.load(File.read('json/consumer.json')),
          :port => CLIENT_CERT_TEST_PORT,
          :ca_path => 'certs/test-ca.crt',
          :insecure => false)

        res = cert_client.get('/status')
        expect(res.content[:message]).to eq("Hello")
      end

      it 'fails to build client when given both consumer and cert info' do
        client_cert = OpenSSL::X509::Certificate.new(File.read('certs/unsigned.crt'))
        client_key = OpenSSL::PKey::RSA.new(File.read('certs/unsigned.key'))
        expect do
          X509Client.from_consumer(
            JSON.load(File.read('json/consumer.json')),
            :port => CLIENT_CERT_TEST_PORT,
            :ca_path => 'certs/test-ca.crt',
            :client_cert => client_cert,
            :client_key => client_key,
            :insecure => false)
        end.to raise_error(ArgumentError)
      end

      it 'builds a client from cert and key files' do
        cert_client = X509Client.from_files(
          'certs/client.crt',
          'certs/client.key',
          :port => CLIENT_CERT_TEST_PORT,
          :ca_path => 'certs/test-ca.crt',
          :insecure => false)

        res = cert_client.get('/status')
        expect(res.content[:message]).to eq("Hello")
      end

      it 'fails to build client when given both cert objects and cert files' do
        client_cert = OpenSSL::X509::Certificate.new(File.read('certs/unsigned.crt'))
        client_key = OpenSSL::PKey::RSA.new(File.read('certs/unsigned.key'))
        expect do
          X509Client.from_files(
            'certs/client.crt',
            'certs/client.key',
            :port => CLIENT_CERT_TEST_PORT,
            :ca_path => 'certs/test-ca.crt',
            :client_cert => client_cert,
            :client_key => client_key,
            :insecure => false)
        end.to raise_error(ArgumentError)
      end

      it 'can select a subset of a hash' do
        original = {
          :x => 1,
          :y => nil,
          :z => 3,
        }
        expected_keys = [:x, :y]
        selected = UtilTest.new.select_from(original, :x, :y)
        expect(selected.keys).to match_array(expected_keys)
      end

      it 'raises an error if not a proper subset' do
        original = {
          :x => 1,
        }
        expect do
          UtilTest.new.select_from(original, :x, :y)
        end.to raise_error(ArgumentError, /Missing keys.*:y/)
      end

      it 'raises an exception on invalid option keys' do
        hash = {
          :good => 'Clint Eastwood',
          :bad => 'Lee Van Cleef',
          :weird => 'Steve Buscemi',
        }
        defaults = { :good => '', :bad => '' }
        msg_regex = /Unknown key: :weird/

        expect do
          UtilTest.new.verify_and_merge(hash, defaults)
        end.to raise_error(ArgumentError, msg_regex)
      end

      it 'verifies valid keys' do
        hash = {
          :good => 'Clint Eastwood',
          :bad => 'Lee Van Cleef',
        }
        defaults = { :good => '', :bad => '' }

        expect do
          UtilTest.new.verify_and_merge(hash, defaults)
        end.not_to raise_error
      end

      it 'turns snake case symbols into camel case symbols' do
        snake = :hello_world
        camel = UtilTest.new.camel_case(snake)
        expect(camel).to eq(:helloWorld)

        snake = :hello
        camel = UtilTest.new.camel_case(snake)
        expect(camel).to eq(:hello)
      end

      it 'turns snake case strings into camel case strings' do
        snake = "hello_world"
        camel = UtilTest.new.camel_case(snake)
        expect(camel).to eq("helloWorld")

        snake = "hello"
        camel = UtilTest.new.camel_case(snake)
        expect(camel).to eq("hello")
      end

      it 'converts hash keys into camel case' do
        h = {
          :hello_world => 'x',
          :y => 'z',
        }
        camel_hash = UtilTest.new.camelize_hash(h)
        expect(camel_hash.keys.sort).to eq([:helloWorld, :y])
      end

      it 'converts hash subsets into camel case' do
        h = {
          :hello_world => 'x',
          :y => 'z',
        }
        camel_hash = UtilTest.new.camelize_hash(h, :hello_world)
        expect(camel_hash.keys.sort).to eq([:helloWorld])
      end

      it 'validation fails for nil keys' do
        h = {
          :x => nil,
          :y => nil,
          :z => true,
        }
        expect do
          UtilTest.new.validate_keys(h)
        end.to raise_error
      end

      it 'validates specific keys' do
        h = {
          :x => nil,
          :y => nil,
          :z => true,
        }
        expect do
          UtilTest.new.validate_keys(h, :z)
        end.to_not raise_error
      end


      it 'validates keys are not nil' do
        h = {
          :z => true,
        }

        expect do
          UtilTest.new.validate_keys(h)
        end.not_to raise_error
      end

      it 'validates keys using a provided block' do
        h = {
          :z => 1,
        }

        expect do
          UtilTest.new.validate_keys(h) do |k|
            k > 5
          end
        end.to raise_error
      end
    end
  end
end